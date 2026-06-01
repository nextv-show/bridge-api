package com.sanshuiyuan.h5.checkout.application;

import com.sanshuiyuan.h5.checkout.api.dto.MiniKycInitResponse;
import com.sanshuiyuan.h5.checkout.domain.KycRecord;
import com.sanshuiyuan.h5.checkout.domain.KycStatus;
import com.sanshuiyuan.h5.checkout.infra.crypto.IdCardCipher;
import com.sanshuiyuan.h5.checkout.infra.crypto.MaskingUtils;
import com.sanshuiyuan.h5.checkout.infra.minikyc.MiniKycProvider;
import com.sanshuiyuan.h5.checkout.infra.repository.KycRecordRepository;
import com.sanshuiyuan.h5.common.BizException;
import com.sanshuiyuan.h5.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 小程序实人认证发起。复用 H5 的 KycRecord 落库 / 加密 / 一证一号 / 已实名短路逻辑，
 * 仅活体提供方换成小程序原生人脸核身（{@link MiniKycProvider}）。
 */
@Service
public class MiniKycInitUseCase {

    private static final Logger log = LoggerFactory.getLogger(MiniKycInitUseCase.class);
    private static final String CHANNEL = "TENCENT_FACEID_MINI";

    private final KycRecordRepository kycRepo;
    private final MiniKycProvider provider;
    private final IdCardCipher cipher;

    public MiniKycInitUseCase(KycRecordRepository kycRepo, MiniKycProvider provider, IdCardCipher cipher) {
        this.kycRepo = kycRepo;
        this.provider = provider;
        this.cipher = cipher;
    }

    @Transactional
    public MiniKycInitResponse execute(String openid, String realName, String idCardNo, String phone) {
        // 已实名短路（含老用户补录手机号），与 KycInitUseCase 一致。
        Optional<KycRecord> existing = kycRepo.findFirstByOpenidAndStatusOrderByVerifiedAtDesc(openid, KycStatus.PASS);
        if (existing.isPresent()) {
            KycRecord r = existing.get();
            String phoneTrim = phone == null ? "" : phone.trim();
            if ((r.getPhoneMask() == null || r.getPhoneMask().isBlank())
                    && !phoneTrim.isEmpty() && phoneTrim.matches("^1[3-9]\\d{9}$")) {
                r.updatePhone(cipher.encrypt(phoneTrim), MaskingUtils.maskPhone(phoneTrim));
                kycRepo.save(r);
                log.info("老用户补录手机号 openid={}", openid);
                return MiniKycInitResponse.alreadyVerified(r.getRealNameMask(), r.getIdCardNoMask(),
                        MaskingUtils.maskPhone(phoneTrim));
            }
            return MiniKycInitResponse.alreadyVerified(r.getRealNameMask(), r.getIdCardNoMask(), r.getPhoneMask());
        }

        String name = realName == null ? "" : realName.trim();
        String idNo = idCardNo == null ? "" : idCardNo.trim().toUpperCase();
        if (name.isEmpty() || name.length() > 64) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "请填写真实姓名");
        }
        if (!IdCardValidator.isValid(idNo)) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "身份证号格式不正确");
        }
        String phoneTrim = phone == null ? "" : phone.trim();
        if (phoneTrim.isEmpty() || !phoneTrim.matches("^1[3-9]\\d{9}$")) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "请填写正确的 11 位手机号");
        }

        // 一证一号（按 id_card_hash，天然跨端正确）。
        String idCardHash = cipher.idCardHash(idNo);
        if (kycRepo.existsByIdCardHashAndStatusAndOpenidNot(idCardHash, KycStatus.PASS, openid)) {
            throw new BizException(ErrorCode.KYC_ID_CARD_CONFLICT);
        }

        MiniKycProvider.MiniKycInitResult result = provider.init(openid, name, idNo);

        KycRecord init = KycRecord.createInit(
                openid, cipher.encrypt(name), cipher.encrypt(idNo),
                MaskingUtils.maskRealName(name), MaskingUtils.maskIdCard(idNo), idCardHash,
                result.certifyId(), CHANNEL,
                cipher.encrypt(phoneTrim), MaskingUtils.maskPhone(phoneTrim));
        kycRepo.save(init);

        return MiniKycInitResponse.init(result.certifyId(), result.sdkParams());
    }
}
