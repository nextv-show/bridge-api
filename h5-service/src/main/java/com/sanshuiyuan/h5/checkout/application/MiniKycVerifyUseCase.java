package com.sanshuiyuan.h5.checkout.application;

import com.sanshuiyuan.h5.checkout.api.dto.KycVerifyResponse;
import com.sanshuiyuan.h5.checkout.domain.KycRecord;
import com.sanshuiyuan.h5.checkout.domain.KycStatus;
import com.sanshuiyuan.h5.checkout.infra.minikyc.MiniKycProvider;
import com.sanshuiyuan.h5.checkout.infra.repository.KycRecordRepository;
import com.sanshuiyuan.h5.common.BizException;
import com.sanshuiyuan.h5.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 小程序实人认证结果确认。与 {@link KycVerifyUseCase} 同构：以 provider 核身结论为权威，
 * 找回 INIT 记录、再校一证一号、作废旧 PASS、promote。仅活体提供方不同。
 */
@Service
public class MiniKycVerifyUseCase {

    private static final Logger log = LoggerFactory.getLogger(MiniKycVerifyUseCase.class);

    private final KycRecordRepository kycRepo;
    private final MiniKycProvider provider;

    public MiniKycVerifyUseCase(KycRecordRepository kycRepo, MiniKycProvider provider) {
        this.kycRepo = kycRepo;
        this.provider = provider;
    }

    @Transactional
    public KycVerifyResponse execute(String certifyId, String openid) {
        MiniKycProvider.MiniKycResult result = provider.queryResult(certifyId);
        if (!result.passed()) {
            return new KycVerifyResponse("FAIL", "", "");
        }

        Optional<KycRecord> initOpt =
                kycRepo.findFirstByCertifyIdAndOpenidAndStatus(certifyId, openid, KycStatus.INIT);
        if (initOpt.isEmpty()) {
            log.warn("核身通过但未找到 INIT 记录 certifyId={}", certifyId);
            return new KycVerifyResponse("FAIL", "", "");
        }
        KycRecord record = initOpt.get();

        String idCardHash = record.getIdCardHash();
        if (idCardHash != null
                && kycRepo.existsByIdCardHashAndStatusAndOpenidNot(idCardHash, KycStatus.PASS, openid)) {
            throw new BizException(ErrorCode.KYC_ID_CARD_CONFLICT);
        }

        List<KycRecord> oldRecords = kycRepo.findAllByOpenidAndStatus(openid, KycStatus.PASS);
        oldRecords.forEach(KycRecord::supersede);
        kycRepo.saveAll(oldRecords);

        record.promoteToPass();
        kycRepo.save(record);

        return new KycVerifyResponse("PASS", record.getRealNameMask(), record.getIdCardNoMask(), record.getPhoneMask());
    }
}
