package com.sanshuiyuan.ess.config;

import com.sanshuiyuan.ess.domain.ContractIdentityVerification;
import com.sanshuiyuan.ess.domain.EssFlowRecord;
import com.sanshuiyuan.ess.domain.FlowStatus;
import com.sanshuiyuan.ess.infra.repository.ContractIdentityVerificationRepository;
import com.sanshuiyuan.ess.infra.repository.EssFlowRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 签署前置校验拦截器。
 * <p>
 * 在启动签署流程（StartFlow）前，强制校验用户身份核验状态。
 * 只有身份核验状态为 PASSED 的用户才能进入签署环节。
 * <p>
 * 校验失败将阻断签署进入支付环节。
 */
@Component
public class SigningPreCheckInterceptor {

    private static final Logger log = LoggerFactory.getLogger(SigningPreCheckInterceptor.class);

    private final ContractIdentityVerificationRepository verificationRepository;
    private final EssFlowRecordRepository flowRecordRepository;

    public SigningPreCheckInterceptor(ContractIdentityVerificationRepository verificationRepository,
                                       EssFlowRecordRepository flowRecordRepository) {
        this.verificationRepository = verificationRepository;
        this.flowRecordRepository = flowRecordRepository;
    }

    /**
     * 校验指定合同的用户是否已通过身份核验。
     * <p>
     * 必须满足以下条件才能放行签署：
     * 1. 合同存在签署流程记录
     * 2. 用户存在身份核验记录
     * 3. 身份核验状态为 PASSED
     *
     * @param contractId 合同 ID
     * @param userId     用户 ID
     * @throws IllegalStateException 如果校验不通过
     */
    public void checkIdentityVerified(String contractId, Long userId) {
        log.debug("签署前置校验 [contractId={}, userId={}]", contractId, userId);

        // 1. 检查签署流程是否存在
        Optional<EssFlowRecord> flowOpt = flowRecordRepository.findByContractId(contractId);
        if (flowOpt.isEmpty()) {
            throw new IllegalStateException(
                    String.format("合同 %s 未创建签署流程", contractId));
        }

        EssFlowRecord flow = flowOpt.get();
        if (flow.getFlowStatus() == FlowStatus.COMPLETED
                || flow.getFlowStatus() == FlowStatus.CANCELLED) {
            throw new IllegalStateException(
                    String.format("合同 %s 已 %s，无法签署", contractId, flow.getFlowStatus()));
        }

        // 2. 检查身份核验记录
        Optional<ContractIdentityVerification> verifyOpt = verificationRepository
                .findTopByContractIdAndUserIdOrderByCreatedAtDesc(contractId, userId);

        if (verifyOpt.isEmpty()) {
            throw new IllegalStateException(
                    String.format("用户 %d 未进行身份核验，请先完成身份核验 [contractId=%s]",
                            userId, contractId));
        }

        ContractIdentityVerification verification = verifyOpt.get();

        // 3. 校验核验状态
        if (verification.getStatus() != ContractIdentityVerification.Status.PASSED) {
            String reason = switch (verification.getStatus()) {
                case PENDING -> "身份核验尚未发起";
                case IN_PROGRESS -> "身份核验进行中，请等待人脸识别完成";
                case FAILED -> "身份核验未通过" +
                        (verification.getFailureReason() != null
                                ? "：" + verification.getFailureReason() : "");
                default -> "未知状态: " + verification.getStatus();
            };

            log.warn("签署前置校验失败 [contractId={}, userId={}, status={}, reason={}]",
                    contractId, userId, verification.getStatus(), reason);

            throw new IllegalStateException(
                    String.format("签署前置校验失败 - %s [contractId=%s, userId=%d]",
                            reason, contractId, userId));
        }

        log.info("签署前置校验通过 [contractId={}, userId={}]", contractId, userId);
    }

    /**
     * 检查是否已通过身份核验（不抛异常版本）。
     *
     * @param contractId 合同 ID
     * @param userId     用户 ID
     * @return 是否已通过
     */
    public boolean isIdentityVerified(String contractId, Long userId) {
        try {
            checkIdentityVerified(contractId, userId);
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }
}
