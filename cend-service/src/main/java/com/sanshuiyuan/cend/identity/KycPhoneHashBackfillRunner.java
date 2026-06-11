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

/**
 * 启动回填：为 V025 之前的已实名(PASS)记录补 phone_hash（解密 phone_enc → HMAC）。
 *
 * <p>没有它，存量返客在 H5 留下的 PASS 记录缺 phone_hash，小程序"微信手机号核验"按号匹配不到 → 无法关联历史订单。
 * 幂等：只处理 phone_hash 为空且有 phone_enc 的记录；逐批推进，整批全失败（如密文损坏）即停，避免空转。
 * 在后台线程执行，不阻塞应用启动。可用 {@code identity.phone-hash-backfill.enabled=false} 关闭。
 */
@Component
@ConditionalOnProperty(name = "identity.phone-hash-backfill.enabled", havingValue = "true", matchIfMissing = true)
public class KycPhoneHashBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KycPhoneHashBackfillRunner.class);
    private static final int BATCH = 200;
    private static final int MAX_BATCHES = 100; // 安全上限：最多 2 万条

    private final KycRecordRepository kycRepo;
    private final IdCardCipher cipher;

    public KycPhoneHashBackfillRunner(KycRecordRepository kycRepo, IdCardCipher cipher) {
        this.kycRepo = kycRepo;
        this.cipher = cipher;
    }

    @Override
    public void run(ApplicationArguments args) {
        Thread t = new Thread(this::backfill, "kyc-phone-hash-backfill");
        t.setDaemon(true);
        t.start();
    }

    private void backfill() {
        int total = 0;
        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            List<KycRecord> page = kycRepo.findByStatusAndPhoneHashIsNullAndPhoneEncIsNotNull(
                    KycStatus.PASS, PageRequest.of(0, BATCH));
            if (page.isEmpty()) {
                break;
            }
            int ok = 0;
            for (KycRecord r : page) {
                try {
                    String phone = cipher.decrypt(r.getPhoneEnc());
                    if (phone != null && !phone.isBlank()) {
                        r.bindPhoneHash(cipher.phoneHash(phone.trim()));
                        kycRepo.save(r);
                        ok++;
                    }
                } catch (Exception e) {
                    log.warn("phone_hash 回填单条失败 id={}: {}", r.getId(), e.getMessage());
                }
            }
            total += ok;
            if (ok == 0) {
                // 整批无进展（剩余均为无法解密的脏数据），停止避免空转。
                log.warn("phone_hash 回填整批无进展，停止（剩余记录需人工排查）");
                break;
            }
        }
        if (total > 0) {
            log.info("phone_hash 回填完成，共补齐 {} 条", total);
        }
    }
}
