package com.sanshuiyuan.cend.identity;

import com.sanshuiyuan.cend.checkout.domain.KycRecord;
import com.sanshuiyuan.cend.checkout.domain.KycStatus;
import com.sanshuiyuan.cend.checkout.infra.crypto.IdCardCipher;
import com.sanshuiyuan.cend.checkout.infra.repository.KycRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

/**
 * 启动回填：为存量已实名(PASS)记录补齐确定性哈希——
 * <ul>
 *   <li><b>phone_hash</b>（V025 之前）：用于"微信手机号核验"按号匹配同人；</li>
 *   <li><b>id_card_hash</b>（V022 之前）：跨端订单聚合与关联的真正连接键。早期 PASS 记录该列为空，
 *       会导致手机号核验命中后 idCardHash 为空 → 关联失败、订单聚合不到。</li>
 * </ul>
 * 两者都解密对应密文（phone_enc / id_card_no_enc）后用同一 HMAC 重算，幂等：只处理为空且有密文的记录；
 * 逐批推进，整批全失败即停避免空转。后台线程执行，不阻塞启动。可用
 * {@code identity.kyc-hash-backfill.enabled=false} 关闭。
 */
@Component
@ConditionalOnProperty(name = "identity.kyc-hash-backfill.enabled", havingValue = "true", matchIfMissing = true)
public class KycHashBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KycHashBackfillRunner.class);
    private static final int BATCH = 200;
    private static final int MAX_BATCHES = 100; // 安全上限：最多 2 万条/类

    private final KycRecordRepository kycRepo;
    private final IdCardCipher cipher;

    public KycHashBackfillRunner(KycRecordRepository kycRepo, IdCardCipher cipher) {
        this.kycRepo = kycRepo;
        this.cipher = cipher;
    }

    @Override
    public void run(ApplicationArguments args) {
        Thread t = new Thread(this::backfillAll, "kyc-hash-backfill");
        t.setDaemon(true);
        t.start();
    }

    void backfillAll() {
        int phone = backfill(
                "phone_hash",
                page -> kycRepo.findByStatusAndPhoneHashIsNullAndPhoneEncIsNotNull(KycStatus.PASS, page),
                r -> {
                    String plain = cipher.decrypt(r.getPhoneEnc());
                    if (plain == null || plain.isBlank()) {
                        return false;
                    }
                    r.bindPhoneHash(cipher.phoneHash(plain.trim()));
                    return true;
                });
        int idCard = backfill(
                "id_card_hash",
                page -> kycRepo.findByStatusAndIdCardHashIsNullAndIdCardNoEncIsNotNull(KycStatus.PASS, page),
                r -> {
                    String plain = cipher.decrypt(r.getIdCardNoEnc());
                    if (plain == null || plain.isBlank()) {
                        return false;
                    }
                    r.bindIdCardHash(cipher.idCardHash(plain.trim().toUpperCase()));
                    return true;
                });
        if (phone > 0 || idCard > 0) {
            log.info("KYC 哈希回填完成：phone_hash {} 条，id_card_hash {} 条", phone, idCard);
        }
    }

    /**
     * 通用回填：反复取一批"缺该哈希且有密文"的 PASS 记录，逐条用 {@code filler} 计算并 save；
     * 整批无进展（如密文损坏）即停。返回补齐条数。
     */
    int backfill(String label, Function<PageRequest, List<KycRecord>> fetcher, Function<KycRecord, Boolean> filler) {
        int total = 0;
        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            List<KycRecord> page = fetcher.apply(PageRequest.of(0, BATCH));
            if (page.isEmpty()) {
                break;
            }
            int ok = 0;
            for (KycRecord r : page) {
                try {
                    if (Boolean.TRUE.equals(filler.apply(r))) {
                        kycRepo.save(r);
                        ok++;
                    }
                } catch (Exception e) {
                    log.warn("{} 回填单条失败 id={}: {}", label, r.getId(), e.getMessage());
                }
            }
            total += ok;
            if (ok == 0) {
                log.warn("{} 回填整批无进展，停止（剩余记录需人工排查）", label);
                break;
            }
        }
        return total;
    }
}
