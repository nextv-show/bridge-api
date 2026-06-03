package com.sanshuiyuan.cend.checkout.application;

import com.sanshuiyuan.cend.checkout.domain.DeviceSpec;
import com.sanshuiyuan.cend.checkout.domain.KycRecord;
import com.sanshuiyuan.cend.checkout.domain.KycStatus;
import com.sanshuiyuan.cend.checkout.infra.crypto.IdCardCipher;
import com.sanshuiyuan.cend.checkout.infra.crypto.MaskingUtils;
import com.sanshuiyuan.cend.checkout.infra.repository.DeviceSpecRepository;
import com.sanshuiyuan.cend.checkout.infra.repository.KycRecordRepository;
import com.sanshuiyuan.cend.common.BizException;
import com.sanshuiyuan.cend.common.ErrorCode;
import com.sanshuiyuan.cend.infra.client.EssServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 小程序认购签约编排：实名+人脸核身由腾讯电子签小程序承载。
 *
 * <p>sign-start：服务端解析规格价格（不信前端）→ 一证一号预检 → 经 ess 生成合同/发起 MINI 签署/取签署参数 →
 * 落 INIT {@link KycRecord}（绑定 certifyId=ESS-{contractId} + 加密实名信息）→ 回跳转参数。
 *
 * <p>sign-status：经 ess 查合同状态，SIGNED/COMPLETED 即把对应 INIT 记录 {@code promoteToPass()}
 * （电子签个人签署已完成实名+人脸核身，故签署达成即视为实名通过）。下游 order/create 的 KYC 闸口随之通过。
 */
@Service
public class SubscribeSigningService {

    private static final Logger log = LoggerFactory.getLogger(SubscribeSigningService.class);
    private static final String CHANNEL = "ESS_SIGN";

    private final KycRecordRepository kycRepo;
    private final DeviceSpecRepository specRepo;
    private final IdCardCipher cipher;
    private final EssServiceClient essClient;
    private final String wxMpAppId;

    public record SignStartResult(Long contractId, String contractNo, Object signParams) {}
    public record KycStatusResult(boolean passed, String realNameMask, String idCardMask, String phoneMask) {}
    private record Identity(String realName, String idCardNo, String phone) {}

    public SubscribeSigningService(KycRecordRepository kycRepo, DeviceSpecRepository specRepo,
                                   IdCardCipher cipher, EssServiceClient essClient,
                                   @Value("${wx.miniprogram.app-id:}") String wxMpAppId) {
        this.kycRepo = kycRepo;
        this.specRepo = specRepo;
        this.cipher = cipher;
        this.essClient = essClient;
        this.wxMpAppId = wxMpAppId;
    }

    public SignStartResult start(String openid, String bearer, Long userId, String specId,
                                 String realName, String idCardNo, String phone) {
        Identity identity = resolveIdentity(openid, realName, idCardNo, phone);
        String name = identity.realName();
        String idNo = identity.idCardNo();
        String phoneTrim = identity.phone();

        // 一证一号：同一身份证已在其他 openid 下 PASS 则拒绝。
        String idCardHash = cipher.idCardHash(idNo);
        if (kycRepo.existsByIdCardHashAndStatusAndOpenidNot(idCardHash, KycStatus.PASS, openid)) {
            throw new BizException(ErrorCode.KYC_ID_CARD_CONFLICT);
        }

        // 服务端解析规格与价格（不信前端）。
        DeviceSpec spec = specRepo.findBySpecIdAndStatus(specId, DeviceSpec.SpecStatus.ACTIVE)
                .orElseThrow(() -> new BizException(ErrorCode.SPEC_NOT_FOUND));
        String devicePrice = BigDecimal.valueOf(spec.getPriceCents()).movePointLeft(2).toPlainString();

        // 经 ess 生成合同 + 发起 MINI 签署 + 取签署参数。
        EssServiceClient.GenerateResult gen = essClient.generate(
                bearer, userId, spec.getModelCode(), devicePrice, name, idNo, phoneTrim);
        essClient.initiateSigning(bearer, gen.contractId(), userId, phoneTrim, name, idNo);
        Object signParams = essClient.signParams(bearer, gen.contractId(), wxMpAppId);

        // 落 INIT KycRecord，绑定本次合同（certifyId=ESS-{contractId}）；签署完成后 promote。
        String certifyId = certifyId(gen.contractId());
        saveInit(openid, name, idNo, idCardHash, phoneTrim, certifyId);

        return new SignStartResult(gen.contractId(), gen.contractNo(), signParams);
    }

    @Transactional(readOnly = true)
    public KycStatusResult currentKycStatus(String openid) {
        return kycRepo.findFirstByOpenidAndStatusOrderByVerifiedAtDesc(openid, KycStatus.PASS)
                .map(r -> new KycStatusResult(true,
                        maskRealName(r),
                        maskIdCard(r),
                        maskPhone(r)))
                .orElse(new KycStatusResult(false, null, null, null));
    }

    private Identity resolveIdentity(String openid, String realName, String idCardNo, String phone) {
        Optional<KycRecord> passOpt = kycRepo.findFirstByOpenidAndStatusOrderByVerifiedAtDesc(openid, KycStatus.PASS);
        if (passOpt.isPresent()) {
            KycRecord record = passOpt.get();
            String name = decryptOrNull(record.getRealName());
            String idNo = decryptOrNull(record.getIdCardNoEnc());
            String phoneTrim = decryptOrNull(record.getPhoneEnc());
            if (name == null || name.isBlank()) {
                name = realName == null ? "" : realName.trim();
            }
            if (idNo == null || idNo.isBlank()) {
                idNo = idCardNo == null ? "" : idCardNo.trim().toUpperCase();
            }
            if (phoneTrim == null || phoneTrim.isBlank()) {
                phoneTrim = phone == null ? "" : phone.trim();
            }
            validateIdentity(name, idNo, phoneTrim);
            return new Identity(name, idNo, phoneTrim);
        }

        String name = realName == null ? "" : realName.trim();
        String idNo = idCardNo == null ? "" : idCardNo.trim().toUpperCase();
        String phoneTrim = phone == null ? "" : phone.trim();
        validateIdentity(name, idNo, phoneTrim);
        return new Identity(name, idNo, phoneTrim);
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
        kycRepo.save(init);
    }

    private String decryptOrNull(byte[] ciphertext) {
        return ciphertext == null ? null : cipher.decrypt(ciphertext);
    }

    private String maskRealName(KycRecord record) {
        String mask = record.getRealNameMask();
        if (mask != null && !mask.isBlank()) {
            return mask;
        }
        String plain = decryptOrNull(record.getRealName());
        return plain == null ? null : MaskingUtils.maskRealName(plain);
    }

    private String maskIdCard(KycRecord record) {
        String mask = record.getIdCardNoMask();
        if (mask != null && !mask.isBlank()) {
            return mask;
        }
        String plain = decryptOrNull(record.getIdCardNoEnc());
        return plain == null ? null : MaskingUtils.maskIdCard(plain);
    }

    private String maskPhone(KycRecord record) {
        String mask = record.getPhoneMask();
        if (mask != null && !mask.isBlank()) {
            return mask;
        }
        String plain = decryptOrNull(record.getPhoneEnc());
        return plain == null ? null : MaskingUtils.maskPhone(plain);
    }

    /**
     * 查询签约状态；SIGNED/COMPLETED 时把对应 INIT 记录提升为 PASS（电子签已完成实名+人脸核身）。
     * 返回归一化后的合同状态字符串。
     */
    @Transactional
    public String status(String openid, String bearer, Long contractId) {
        String status = essClient.status(bearer, contractId);
        if ("SIGNED".equals(status) || "COMPLETED".equals(status)) {
            promoteKyc(openid, certifyId(contractId));
        }
        return status;
    }

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
            throw new BizException(ErrorCode.KYC_ID_CARD_CONFLICT);
        }
        // 作废该 openid 旧 PASS（一 openid 一实名）。
        List<KycRecord> old = kycRepo.findAllByOpenidAndStatus(openid, KycStatus.PASS);
        old.forEach(KycRecord::supersede);
        kycRepo.saveAll(old);

        record.promoteToPass();
        kycRepo.save(record);
        log.info("电子签完成，KYC 提升为 PASS openid={} certifyId={}", openid, certifyId);
    }

    private static String certifyId(Long contractId) {
        return "ESS-" + contractId;
    }
}
