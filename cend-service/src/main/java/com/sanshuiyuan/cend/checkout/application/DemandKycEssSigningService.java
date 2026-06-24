package com.sanshuiyuan.cend.checkout.application;

import com.sanshuiyuan.cend.checkout.domain.KycRecord;
import com.sanshuiyuan.cend.checkout.domain.KycStatus;
import com.sanshuiyuan.cend.checkout.infra.crypto.IdCardCipher;
import com.sanshuiyuan.cend.checkout.infra.crypto.MaskingUtils;
import com.sanshuiyuan.cend.checkout.infra.repository.KycRecordRepository;
import com.sanshuiyuan.cend.common.BizException;
import com.sanshuiyuan.cend.common.ErrorCode;
import com.sanshuiyuan.cend.infra.client.EssServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 发布用水需求前的「实名认证 / 用水需求发布承诺」电子签编排（spec 107）。
 *
 * <p>与设备认购签约（{@link SubscribeSigningService}）刻意隔离，互不复用 {@code specId} 语义与合同模板：
 * <ul>
 *   <li>专用 {@code channel = ESS_KYC_AUTH}、{@code certifyId = ESS-KYC-{contractId}}；</li>
 *   <li>经 ess-service 以 {@code contractPurpose=KYC_AUTH} 生成《三水元实名认证与用水需求发布承诺书》，
 *       {@code notify=true} 由腾讯电子签下发签署短信短链；</li>
 *   <li>SIGNED/COMPLETED/ARCHIVED 即把对应 INIT 记录幂等提升为 {@code PASS}，下游
 *       matching-service 的发布需求强实名门控随之放行（门控口径不变，仍只认 PASS）。</li>
 * </ul>
 *
 * <p>实名状态查询直接复用 {@link SubscribeSigningService#currentKycStatus(String)}（同一 {@code kyc_records} 事实源）。
 */
@Service
public class DemandKycEssSigningService {

    private static final Logger log = LoggerFactory.getLogger(DemandKycEssSigningService.class);
    private static final String CHANNEL = "ESS_KYC_AUTH";

    /**
     * 同一 openid + 渠道发起签署的串行化锁（分段锁，单 JVM 内幂等兜底）。
     *
     * <p>固定段数避免按 key 无限增长；锁在事务外获取（见 {@link #start}），覆盖
     * 「查 PASS → 查 INIT → 生成合同 → 发起签署 → 落 INIT 并提交」整个临界区，
     * 杜绝同一 openid 并发首发各自生成合同 / 重发短信。
     */
    private static final int LOCK_STRIPES = 64;
    private final Object[] startLocks = new Object[LOCK_STRIPES];

    private final KycRecordRepository kycRepo;
    private final IdCardCipher cipher;
    private final EssServiceClient essClient;
    private final SubscribeSigningService subscribeSigningService;

    /**
     * 自身代理引用：锁需在事务外获取，故 {@link #start} 不加 {@code @Transactional}，
     * 在临界区内经代理调用 {@link #startInTransaction} 以触发真实事务边界（提交发生在锁内）。
     * 单元测试经 {@code new} 直接构造时 {@code self} 为 null，回退为 {@code this}（无 Spring 事务，逻辑等价）。
     */
    @Autowired
    @Lazy
    private DemandKycEssSigningService self;

    public record EssSignStartResult(boolean alreadyPassed, Long contractId, String contractNo, String phoneMask) {}

    public DemandKycEssSigningService(KycRecordRepository kycRepo, IdCardCipher cipher,
                                      EssServiceClient essClient,
                                      SubscribeSigningService subscribeSigningService) {
        this.kycRepo = kycRepo;
        this.cipher = cipher;
        this.essClient = essClient;
        this.subscribeSigningService = subscribeSigningService;
        for (int i = 0; i < LOCK_STRIPES; i++) {
            this.startLocks[i] = new Object();
        }
    }

    /** 当前用户实名状态（复用认购侧同一事实源逻辑）。 */
    @Transactional(readOnly = true)
    public SubscribeSigningService.KycStatusResult currentKycStatus(String openid) {
        return subscribeSigningService.currentKycStatus(openid);
    }

    /**
     * 发起实名承诺电子签（串行化入口）。
     *
     * <p>按 {@code openid + 渠道} 取分段锁，在锁内经代理调用 {@link #startInTransaction}，使
     * 「查 PASS → 查 INIT → 生成合同 → 发起签署 → 落 INIT 提交」整段在临界区内串行完成。
     * 由此覆盖同一 openid 的并发首发：先到者落 INIT 并提交后释放锁，后到者进入临界区即命中
     * 「已 PASS」或「未完成 INIT 复用」分支，不再重复生成合同 / 重发签署短信。
     *
     * <p>锁在事务外（本方法不加 {@code @Transactional}），保证提交发生在持锁期间；
     * 跨实例并发仍依赖既有「PASS / INIT 复用」幂等读 + ess 侧合同语义兜底。
     */
    public EssSignStartResult start(String openid, String bearer, Long userId,
                                    String realName, String idCardNo, String phone) {
        synchronized (lockFor(openid)) {
            return self().startInTransaction(openid, bearer, userId, realName, idCardNo, phone);
        }
    }

    /**
     * 实名承诺电子签事务体（务必经代理在 {@link #start} 持锁后调用）。
     * <p>已 PASS：直接返回 {@code alreadyPassed=true}，不新建合同、不 supersede 旧 PASS。
     * 未 PASS：经 ess 生成实名承诺合同 + 发起短信短链签署，并落 INIT {@link KycRecord}。
     */
    @Transactional
    public EssSignStartResult startInTransaction(String openid, String bearer, Long userId,
                                                 String realName, String idCardNo, String phone) {
        // 已实名直接返回，避免重复发起签署。
        if (kycRepo.findFirstByOpenidAndStatusOrderByVerifiedAtDesc(openid, KycStatus.PASS).isPresent()) {
            log.info("实名承诺签署：当前 openid 已 PASS，直接返回 openid={}", openid);
            return new EssSignStartResult(true, null, null, null);
        }

        // 幂等重入：未 PASS 但已存在未完成 INIT（同一 openid + ESS_KYC_AUTH，certifyId 形如 ESS-KYC-{contractId}）时，
        // 复用原合同，不再调 ess 生成新合同 / 重发签署短信，避免重复点击产生多份有效待签承诺书。
        Optional<KycRecord> pendingInit =
                kycRepo.findFirstByOpenidAndChannelAndStatusOrderByIdDesc(openid, CHANNEL, KycStatus.INIT);
        if (pendingInit.isPresent()) {
            Long reusedContractId = parseContractId(pendingInit.get().getCertifyId());
            if (reusedContractId != null) {
                log.info("实名承诺签署幂等：复用未完成 INIT 合同，不重复发起 openid={} contractId={}",
                        openid, reusedContractId);
                return new EssSignStartResult(false, reusedContractId, null, pendingInit.get().getPhoneMask());
            }
        }

        String name = realName == null ? "" : realName.trim();
        String idNo = idCardNo == null ? "" : idCardNo.trim().toUpperCase();
        String phoneTrim = phone == null ? "" : phone.trim();
        validateIdentity(name, idNo, phoneTrim);

        // 一证一号（同证同人则链接）：仅记审计、不拦截，签署完成置 PASS 时建立跨端绑定。
        String idCardHash = cipher.idCardHash(idNo);
        if (kycRepo.existsByIdCardHashAndStatusAndOpenidNot(idCardHash, KycStatus.PASS, openid)) {
            log.info("一证一号跨端链接：同证已在其他 openid PASS，放行本端发起实名承诺签署 openid={}", openid);
        }

        // 经 ess 生成《实名认证与用水需求发布承诺书》(KYC_AUTH) + 发起 MINI 短信短链签署（notify=true）。
        EssServiceClient.GenerateResult gen = essClient.generateKycAuth(bearer, userId, name, idNo, phoneTrim);
        essClient.initiateSigning(bearer, gen.contractId(), userId, phoneTrim, name, idNo);

        // 落 INIT KycRecord，绑定本次合同（certifyId=ESS-KYC-{contractId}）；签署完成后 promote。
        saveInit(openid, name, idNo, idCardHash, phoneTrim, certifyId(gen.contractId()));

        return new EssSignStartResult(false, gen.contractId(), gen.contractNo(), MaskingUtils.maskPhone(phoneTrim));
    }

    /**
     * 查询实名承诺签署状态；SIGNED/COMPLETED/ARCHIVED 即把对应 INIT 记录幂等提升为 PASS。
     * 返回归一化后的合同状态字符串（完成统一返回 {@code SIGNED}）。
     */
    @Transactional
    public String status(String openid, String bearer, Long contractId) {
        String status = essClient.status(bearer, contractId);
        if (isSigningComplete(status)) {
            promoteKyc(openid, certifyId(contractId));
            return "SIGNED";
        }
        return status;
    }

    private void validateIdentity(String name, String idNo, String phoneTrim) {
        if (name.isEmpty() || name.length() > 64) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "请填写真实姓名");
        }
        if (!IdCardValidator.isValid(idNo)) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "身份证号格式不正确");
        }
        if (!phoneTrim.matches("^1[3-9]\\d{9}$")) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "请填写正确的 11 位手机号");
        }
    }

    private void saveInit(String openid, String name, String idNo, String idCardHash,
                          String phone, String certifyId) {
        // 同一 contract 重复发起：清掉旧 INIT，避免堆积。
        kycRepo.findFirstByCertifyIdAndOpenidAndStatus(certifyId, openid, KycStatus.INIT)
                .ifPresent(kycRepo::delete);
        KycRecord init = KycRecord.createInit(
                openid, cipher.encrypt(name), cipher.encrypt(idNo),
                MaskingUtils.maskRealName(name), MaskingUtils.maskIdCard(idNo), idCardHash,
                certifyId, CHANNEL,
                cipher.encrypt(phone), MaskingUtils.maskPhone(phone));
        init.bindPhoneHash(cipher.phoneHash(phone));
        kycRepo.save(init);
    }

    /** 「签署已完成」判定：SIGNED 及其之后的状态（COMPLETED/ARCHIVED）。 */
    private static boolean isSigningComplete(String status) {
        return "SIGNED".equals(status) || "COMPLETED".equals(status) || "ARCHIVED".equals(status);
    }

    /** 仅提升当前 openid + 本次 certifyId 对应的 INIT 记录（防越权 / 防误升其他记录）。 */
    private void promoteKyc(String openid, String certifyId) {
        Optional<KycRecord> initOpt =
                kycRepo.findFirstByCertifyIdAndOpenidAndStatus(certifyId, openid, KycStatus.INIT);
        if (initOpt.isEmpty()) {
            return; // 已提升或无记录，幂等返回。
        }
        KycRecord record = initOpt.get();
        String idCardHash = record.getIdCardHash();
        if (idCardHash != null
                && kycRepo.existsByIdCardHashAndStatusAndOpenidNot(idCardHash, KycStatus.PASS, openid)) {
            log.info("一证一号跨端链接：同证已在其他 openid PASS，实名承诺签署完成建立同人绑定 openid={}", openid);
        }
        // 作废该 openid 旧 PASS（一 openid 一实名）。
        List<KycRecord> old = kycRepo.findAllByOpenidAndStatus(openid, KycStatus.PASS);
        old.forEach(KycRecord::supersede);
        kycRepo.saveAll(old);

        record.promoteToPass();
        kycRepo.save(record);
        log.info("实名承诺签署完成，KYC 提升为 PASS openid={} certifyId={}", openid, certifyId);
    }

    /** 取 {@code openid + 渠道} 对应的分段锁对象（固定段数，按 hash 落段）。 */
    private Object lockFor(String openid) {
        String key = (openid == null ? "" : openid) + '|' + CHANNEL;
        return startLocks[Math.floorMod(key.hashCode(), LOCK_STRIPES)];
    }

    /** 自身代理；Spring 注入时返回事务代理，单元测试直接构造时回退 {@code this}。 */
    private DemandKycEssSigningService self() {
        return self != null ? self : this;
    }

    private static String certifyId(Long contractId) {
        return "ESS-KYC-" + contractId;
    }

    /** 从 certifyId（形如 {@code ESS-KYC-{contractId}}）解析合同 ID；非法格式返回 null。 */
    private static Long parseContractId(String certifyId) {
        if (certifyId == null || !certifyId.startsWith("ESS-KYC-")) {
            return null;
        }
        try {
            return Long.valueOf(certifyId.substring("ESS-KYC-".length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
