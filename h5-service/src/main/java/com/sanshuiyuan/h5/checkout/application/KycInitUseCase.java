package com.sanshuiyuan.h5.checkout.application;

import com.sanshuiyuan.h5.checkout.api.dto.KycInitResponse;
import com.sanshuiyuan.h5.checkout.domain.KycRecord;
import com.sanshuiyuan.h5.checkout.domain.KycStatus;
import com.sanshuiyuan.h5.checkout.infra.aliyun.AliyunKycClient;
import com.sanshuiyuan.h5.checkout.infra.crypto.IdCardCipher;
import com.sanshuiyuan.h5.checkout.infra.crypto.MaskingUtils;
import com.sanshuiyuan.h5.checkout.infra.repository.KycRecordRepository;
import com.sanshuiyuan.h5.common.BizException;
import com.sanshuiyuan.h5.common.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class KycInitUseCase {

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
        // 阿里云认证完成后回跳的 H5 页面。前端用 HashRouter，checkout 在 /#/checkout；
        // 阿里云会在该地址后追加 ?response=<json>（含 certifyId），前端从真实 query 解析。
        this.returnUrl = stripTrailingSlash(publicBaseUrl) + "/#/checkout";
    }

    @Transactional
    public KycInitResponse execute(String openid, String metaInfo, String realName, String idCardNo) {
        // Check if already verified (ASSUMPTION-Q2: don't re-init for PASS users)
        Optional<KycRecord> existing = kycRepo.findFirstByOpenidAndStatusOrderByVerifiedAtDesc(openid, KycStatus.PASS);
        if (existing.isPresent()) {
            KycRecord r = existing.get();
            return KycInitResponse.alreadyVerified(r.getRealNameMask(), r.getIdCardNoMask());
        }

        // LR_FR 活体方案不回传身份信息，需前端采集姓名 + 身份证号，后端校验后加密绑定到本次 certifyId。
        String name = realName == null ? "" : realName.trim();
        String idNo = idCardNo == null ? "" : idCardNo.trim().toUpperCase();
        if (name.isEmpty() || name.length() > 64) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "请填写真实姓名");
        }
        if (!IdCardValidator.isValid(idNo)) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "身份证号格式不正确");
        }

        AliyunKycClient.KycInitResult result = kycClient.init(openid, metaInfo, returnUrl);

        // 落 INIT 记录，把实名信息加密绑定到 certifyId，活体通过后再 promote 为 PASS。
        byte[] nameEnc = cipher.encrypt(name);
        byte[] idEnc = cipher.encrypt(idNo);
        KycRecord init = KycRecord.createInit(
                openid, nameEnc, idEnc,
                MaskingUtils.maskRealName(name), MaskingUtils.maskIdCard(idNo),
                result.certifyId(), "ALIYUN_LR_FR");
        kycRepo.save(init);

        return KycInitResponse.init(result.certifyId(), result.verifyToken(), result.verifyUrl());
    }

    private static String stripTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
