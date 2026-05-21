package com.sanshuiyuan.h5.checkout.application;

import com.sanshuiyuan.h5.checkout.api.dto.KycInitResponse;
import com.sanshuiyuan.h5.checkout.domain.KycRecord;
import com.sanshuiyuan.h5.checkout.domain.KycStatus;
import com.sanshuiyuan.h5.checkout.infra.aliyun.AliyunKycClient;
import com.sanshuiyuan.h5.checkout.infra.repository.KycRecordRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class KycInitUseCase {

    private final KycRecordRepository kycRepo;
    private final AliyunKycClient kycClient;

    public KycInitUseCase(KycRecordRepository kycRepo, AliyunKycClient kycClient) {
        this.kycRepo = kycRepo;
        this.kycClient = kycClient;
    }

    public KycInitResponse execute(String openid) {
        // Check if already verified (ASSUMPTION-Q2: don't re-init for PASS users)
        Optional<KycRecord> existing = kycRepo.findFirstByOpenidAndStatusOrderByVerifiedAtDesc(openid, KycStatus.PASS);
        if (existing.isPresent()) {
            KycRecord r = existing.get();
            return KycInitResponse.alreadyVerified(r.getRealNameMask(), r.getIdCardNoMask());
        }

        // Init new verification
        AliyunKycClient.KycInitResult result = kycClient.init(openid);
        return KycInitResponse.init(result.certifyId(), result.verifyToken(), result.verifyUrl());
    }
}
