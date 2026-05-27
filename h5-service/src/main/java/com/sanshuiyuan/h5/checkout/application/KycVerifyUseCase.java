package com.sanshuiyuan.h5.checkout.application;

import com.sanshuiyuan.h5.checkout.api.dto.KycVerifyResponse;
import com.sanshuiyuan.h5.checkout.domain.KycRecord;
import com.sanshuiyuan.h5.checkout.domain.KycStatus;
import com.sanshuiyuan.h5.checkout.infra.aliyun.AliyunKycClient;
import com.sanshuiyuan.h5.checkout.infra.repository.KycRecordRepository;
import com.sanshuiyuan.h5.common.BizException;
import com.sanshuiyuan.h5.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class KycVerifyUseCase {

    private static final Logger log = LoggerFactory.getLogger(KycVerifyUseCase.class);

    private final KycRecordRepository kycRepo;
    private final AliyunKycClient kycClient;

    public KycVerifyUseCase(KycRecordRepository kycRepo, AliyunKycClient kycClient) {
        this.kycRepo = kycRepo;
        this.kycClient = kycClient;
    }

    @Transactional
    public KycVerifyResponse execute(String certifyId, String openid) {
        // 以阿里云活体检测结果为权威（不信任前端）。
        AliyunKycClient.KycVerifyResult result = kycClient.queryResult(certifyId);
        if (!result.passed()) {
            return new KycVerifyResponse("FAIL", "", "");
        }

        // 取本次发起时落的 INIT 记录（已加密绑定前端采集的姓名+身份证号）。
        Optional<KycRecord> initOpt =
                kycRepo.findFirstByCertifyIdAndOpenidAndStatus(certifyId, openid, KycStatus.INIT);
        if (initOpt.isEmpty()) {
            // 无对应 INIT 记录（异常路径，如重复回调/越权）。活体虽过但缺实名绑定，拒绝置 PASS。
            log.warn("活体通过但未找到 INIT 记录 certifyId={}", certifyId);
            return new KycVerifyResponse("FAIL", "", "");
        }
        KycRecord record = initOpt.get();

        // 一证一号（权威闸口）：活体已过，但若同一身份证号已在其他 openid 下 PASS，则拒绝置 PASS。
        String idCardHash = record.getIdCardHash();
        if (idCardHash != null
                && kycRepo.existsByIdCardHashAndStatusAndOpenidNot(idCardHash, KycStatus.PASS, openid)) {
            throw new BizException(ErrorCode.KYC_ID_CARD_CONFLICT);
        }

        // 作废该用户旧的 PASS 记录（ASSUMPTION-Q6：一 openid 对应一实名）。
        List<KycRecord> oldRecords = kycRepo.findAllByOpenidAndStatus(openid, KycStatus.PASS);
        oldRecords.forEach(KycRecord::supersede);
        kycRepo.saveAll(oldRecords);

        record.promoteToPass();
        kycRepo.save(record);

        return new KycVerifyResponse("PASS", record.getRealNameMask(), record.getIdCardNoMask());
    }
}
