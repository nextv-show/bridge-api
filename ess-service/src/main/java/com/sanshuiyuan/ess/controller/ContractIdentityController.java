package com.sanshuiyuan.ess.controller;

import com.sanshuiyuan.ess.domain.ContractIdentityVerification;
import com.sanshuiyuan.ess.infra.repository.ContractIdentityVerificationRepository;
import com.sanshuiyuan.ess.service.IdentityCheckService;
import com.sanshuiyuan.ess.service.IdentityVerificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 合同签署前身份核验控制器。
 * <p>
 * 提供签署前的身份核验全流程：
 * - 前置 KYC 检查
 * - 发起腾讯电子签人脸识别
 * - 接收核验结果回调
 * - 查询核验状态
 * <p>
 * 所有端点在 /api/h5/contracts/{contractId}/identity-* 路径下。
 */
@RestController
@RequestMapping("/api/h5/contracts")
public class ContractIdentityController {

    private static final Logger log = LoggerFactory.getLogger(ContractIdentityController.class);

    private static final int MAX_RETRIES_PER_DAY = 3;

    private final IdentityCheckService identityCheckService;
    private final IdentityVerificationService verificationService;
    private final ContractIdentityVerificationRepository verificationRepository;

    public ContractIdentityController(IdentityCheckService identityCheckService,
                                       IdentityVerificationService verificationService,
                                       ContractIdentityVerificationRepository verificationRepository) {
        this.identityCheckService = identityCheckService;
        this.verificationService = verificationService;
        this.verificationRepository = verificationRepository;
    }

    // ========== T18.4: GET /identity-check ==========

    /**
     * 签署前身份检查。
     * <p>
     * 检查用户 KYC 状态和已有核验记录，返回是否需要重新核验。
     *
     * @param contractId 合同 ID
     * @param userId     用户 ID（通过请求头或 query param 传入）
     * @return 身份检查结果
     */
    @GetMapping("/{contractId}/identity-check")
    public ResponseEntity<Map<String, Object>> identityCheck(
            @PathVariable String contractId,
            @RequestParam Long userId) {

        log.info("签署前身份检查 [contractId={}, userId={}]", contractId, userId);

        // 1. 检查 KYC 状态
        IdentityCheckService.KycStatusInfo kycInfo = identityCheckService.checkKycStatus(userId);
        if (!kycInfo.verified()) {
            return ResponseEntity.ok(Map.of(
                    "code", 1,
                    "status", "KYC_NOT_VERIFIED",
                    "message", "用户未完成实名认证，请先完成 KYC",
                    "needKyc", true
            ));
        }

        // 2. 查找已有核验记录
        var existingOpt = verificationRepository
                .findTopByContractIdAndUserIdOrderByCreatedAtDesc(contractId, userId);

        if (existingOpt.isPresent()) {
            ContractIdentityVerification existing = existingOpt.get();
            if (existing.getStatus() == ContractIdentityVerification.Status.PASSED) {
                return ResponseEntity.ok(Map.of(
                        "code", 0,
                        "status", "PASSED",
                        "message", "身份核验已通过",
                        "needKyc", false,
                        "needVerify", false,
                        "verifiedAt", existing.getVerifiedAt() != null
                                ? existing.getVerifiedAt().toString() : null
                ));
            }
        }

        // 3. 检查当日重试次数
        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        long todayAttempts = verificationRepository
                .countByUserIdAndCreatedAtAfter(userId, todayStart);

        if (todayAttempts >= MAX_RETRIES_PER_DAY) {
            return ResponseEntity.ok(Map.of(
                    "code", 2,
                    "status", "RETRY_LIMIT_EXCEEDED",
                    "message", "今日核验次数已达上限（" + MAX_RETRIES_PER_DAY + "次）",
                    "needKyc", false,
                    "needVerify", true,
                    "retryCount", todayAttempts,
                    "maxRetries", MAX_RETRIES_PER_DAY
            ));
        }

        // 4. 需要进行身份核验
        return ResponseEntity.ok(Map.of(
                "code", 0,
                "status", "NEED_VERIFY",
                "message", "需要进行身份核验",
                "needKyc", false,
                "needVerify", true,
                "retryCount", todayAttempts,
                "maxRetries", MAX_RETRIES_PER_DAY,
                "kycRecordId", kycInfo.kycRecordId()
        ));
    }
}
