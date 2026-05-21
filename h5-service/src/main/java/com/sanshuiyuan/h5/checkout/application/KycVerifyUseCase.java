package com.sanshuiyuan.h5.checkout.application;

import com.sanshuiyuan.h5.checkout.api.dto.KycVerifyResponse;
import com.sanshuiyuan.h5.checkout.domain.KycRecord;
import com.sanshuiyuan.h5.checkout.domain.KycStatus;
import com.sanshuiyuan.h5.checkout.infra.aliyun.AliyunKycClient;
import com.sanshuiyuan.h5.checkout.infra.crypto.IdCardCipher;
import com.sanshuiyuan.h5.checkout.infra.crypto.MaskingUtils;
import com.sanshuiyuan.h5.checkout.infra.repository.KycRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class KycVerifyUseCase {

    private final KycRecordRepository kycRepo;
    private final AliyunKycClient kycClient;
    private final IdCardCipher cipher;

    public KycVerifyUseCase(KycRecordRepository kycRepo, AliyunKycClient kycClient, IdCardCipher cipher) {
        this.kycRepo = kycRepo;
        this.kycClient = kycClient;
        this.cipher = cipher;
    }

    @Transactional
    public KycVerifyResponse execute(String certifyId, String openid) {
        // Query authoritative result from Aliyun (don't trust frontend)
        AliyunKycClient.KycVerifyResult result = kycClient.queryResult(certifyId);

        if (!result.passed()) {
            return new KycVerifyResponse("FAIL", "", "");
        }

        // Encrypt sensitive data
        byte[] idCardEnc = cipher.encrypt(result.idCardNo());
        byte[] realNameEnc = cipher.encrypt(result.realName());
        String realNameMask = MaskingUtils.maskRealName(result.realName());
        String idCardMask = MaskingUtils.maskIdCard(result.idCardNo());

        // Supersede old PASS records (ASSUMPTION-Q6: one-to-one openid↔identity)
        List<KycRecord> oldRecords = kycRepo.findAll().stream()
                .filter(r -> r.getOpenid().equals(openid) && r.getStatus() == KycStatus.PASS)
                .toList();
        oldRecords.forEach(KycRecord::supersede);
        kycRepo.saveAll(oldRecords);

        // Create new PASS record
        KycRecord record = KycRecord.create(
                openid, realNameEnc, idCardEnc, realNameMask, idCardMask,
                certifyId, "ALIYUN_FINANCE"
        );
        kycRepo.save(record);

        return new KycVerifyResponse("PASS", realNameMask, idCardMask);
    }
}
