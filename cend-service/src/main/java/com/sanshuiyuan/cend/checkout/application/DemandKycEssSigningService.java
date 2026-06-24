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

    private final KycRecordRepository kycRepo;
    private final IdCardCipher cipher;
    private final EssServiceClient essClient;
    private final SubscribeSigningService subscribeSigningService;

    public record EssSignStartResult(boolean alreadyPassed, Long contractId, String contractNo, String phoneMask) {}

    public DemandKycEssSigningService(KycRecordRepository kycRepo, IdCardCipher cipher,
                                      EssServiceClient essClient,
                                      SubscribeSigningService subscribeSigningService) {
        this.kycRepo = kycRepo;
        this.cipher = cipher;
        this.essClient = essClient;
        this.subscribeSigningService = subscribeSigningService;
    }

    /** 当前用户实名状态（复用认购侧同一事实源逻辑）。 */
    @Transactional(readOnly = true)
    public SubscribeSigningService.KycStatusResult currentKycStatus(String openid) {
        return subscribeSigningService.currentKycStatus(openid);
    }

    /**
     * 发起实名承诺电子签。
     * <p>已 PASS：直接返回 {@code alreadyPassed=true}，不新建合同、不 supersede 旧 PASS。
     * 未 PASS：经 ess 生成实名承诺合同 + 发起短信短链签署，并落 INIT {@link KycRecord}。
     */
    @Transactional
    public EssSignStartResult start(String openid, String bearer, Long userId,
                                    String realName, String idCardNo, String phone) {
        // 已实名直接返回，避免重复发起签署。
        if (kycRepo.findFirstByOpenidAndStatusOrderByVerifiedAtDesc(openid, KycStatus.PASS).isPresent()) {
            log.info("实名承诺签署：当前 openid 已 PASS，直接返回 openid={}", openid);
            return new EssSignStartResult(true, null, null, null);
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

    private static String certifyId(Long contractId) {
        return "ESS-KYC-" + contractId;
    }
}
