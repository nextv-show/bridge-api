package com.sanshuiyuan.cend.checkout.application;

import com.sanshuiyuan.cend.checkout.api.dto.KycInitResponse;
import com.sanshuiyuan.cend.checkout.domain.KycRecord;
import com.sanshuiyuan.cend.checkout.domain.KycStatus;
import com.sanshuiyuan.cend.checkout.infra.aliyun.AliyunKycClient;
import com.sanshuiyuan.cend.checkout.infra.crypto.IdCardCipher;
import com.sanshuiyuan.cend.checkout.infra.crypto.MaskingUtils;
import com.sanshuiyuan.cend.checkout.infra.repository.KycRecordRepository;
import com.sanshuiyuan.cend.common.BizException;
import com.sanshuiyuan.cend.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class KycInitUseCase {

    private static final Logger log = LoggerFactory.getLogger(KycInitUseCase.class);

    private final KycRecordRepository kycRepo;
    private final AliyunKycClient kycClient;
    private final IdCardCipher cipher;
    private final String returnUrl;

    public KycInitUseCase(KycRecordRepository kycRepo,
                          AliyunKycClient kycClient,
                          IdCardCipher cipher,
                          @Value("${h5.public-base-url}") String publicBaseUrl) {
        this.kycRepo = kycRepo;
        this.kycClient = kycClient;
        this.cipher = cipher;
        this.returnUrl = stripTrailingSlash(publicBaseUrl) + "/#/checkout";
    }

    @Transactional
    public KycInitResponse execute(String openid, String metaInfo, String realName, String idCardNo, String phone) {
        // Check if already verified
        Optional<KycRecord> existing = kycRepo.findFirstByOpenidAndStatusOrderByVerifiedAtDesc(openid, KycStatus.PASS);
        if (existing.isPresent()) {
            KycRecord r = existing.get();
            // 老用户补录手机号：DB 中无手机号但本次请求带了合法手机号 → 加密存储
            String phoneTrim = phone == null ? "" : phone.trim();
            if ((r.getPhoneMask() == null || r.getPhoneMask().isBlank())
                    && !phoneTrim.isEmpty() && phoneTrim.matches("^1[3-9]\\d{9}$")) {
                byte[] phoneEnc = cipher.encrypt(phoneTrim);
                String phoneMask = MaskingUtils.maskPhone(phoneTrim);
                r.updatePhone(phoneEnc, phoneMask);
                kycRepo.save(r);
                log.info("老用户补录手机号 openid={} phoneMask={}", openid, phoneMask);
                return KycInitResponse.alreadyVerified(r.getRealNameMask(), r.getIdCardNoMask(), phoneMask);
            }
            return KycInitResponse.alreadyVerified(r.getRealNameMask(), r.getIdCardNoMask(), r.getPhoneMask());
        }

        // Validate name
        String name = realName == null ? "" : realName.trim();
        String idNo = idCardNo == null ? "" : idCardNo.trim().toUpperCase();
        if (name.isEmpty() || name.length() > 64) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "请填写真实姓名");
        }
        if (!IdCardValidator.isValid(idNo)) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "身份证号格式不正确");
        }

        // Validate phone (required for e-contract signing)
        String phoneTrim = phone == null ? "" : phone.trim();
        if (phoneTrim.isEmpty() || !phoneTrim.matches("^1[3-9]\\d{9}$")) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "请填写正确的 11 位手机号");
        }

        // 一证一号
        String idCardHash = cipher.idCardHash(idNo);
        if (kycRepo.existsByIdCardHashAndStatusAndOpenidNot(idCardHash, KycStatus.PASS, openid)) {
            throw new BizException(ErrorCode.KYC_ID_CARD_CONFLICT);
        }

        AliyunKycClient.KycInitResult result = kycClient.init(openid, metaInfo, returnUrl);

        // 加密存储：姓名、身份证号、手机号
        byte[] nameEnc = cipher.encrypt(name);
        byte[] idEnc = cipher.encrypt(idNo);
        byte[] phoneEnc = cipher.encrypt(phoneTrim);
        String phoneMask = MaskingUtils.maskPhone(phoneTrim);
        KycRecord init = KycRecord.createInit(
                openid, nameEnc, idEnc,
                MaskingUtils.maskRealName(name), MaskingUtils.maskIdCard(idNo), idCardHash,
                result.certifyId(), "ALIYUN_LR_FR",
                phoneEnc, phoneMask);
        kycRepo.save(init);

        return KycInitResponse.init(result.certifyId(), result.verifyToken(), result.verifyUrl());
    }

    private static String stripTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
