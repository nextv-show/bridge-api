package com.sanshuiyuan.ess.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanshuiyuan.ess.config.EssProperties;
import com.sanshuiyuan.ess.domain.ContractIdentityVerification;
import com.sanshuiyuan.ess.domain.ContractIdentityVerification.VerificationType;
import com.sanshuiyuan.ess.infra.client.EssApiClient;
import com.sanshuiyuan.ess.infra.repository.ContractIdentityVerificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.TreeMap;

/**
 * 身份核验服务。
 * <p>
 * 调用腾讯电子签身份核验 API，发起二次人脸识别，
 * 管理核验记录的生命周期。
 * <p>
 * 核心流程：
 * 1. 检查 KYC → 2. 创建核验记录 → 3. 调用 ESS DetectIdentityFace →
 * 4. 返回人脸识别参数 → 5. 等待回调 → 6. 更新核验结果
 */
@Service
public class IdentityVerificationService {

    private static final Logger log = LoggerFactory.getLogger(IdentityVerificationService.class);
    private static final int MAX_RETRIES_PER_DAY = 3;

    private final EssApiClient apiClient;
    private final EssProperties properties;
    private final ContractIdentityVerificationRepository verificationRepository;
    private final IdentityCheckService identityCheckService;
    private final ObjectMapper objectMapper;

    public IdentityVerificationService(EssApiClient apiClient,
                                        EssProperties properties,
                                        ContractIdentityVerificationRepository verificationRepository,
                                        IdentityCheckService identityCheckService,
                                        ObjectMapper objectMapper) {
        this.apiClient = apiClient;
        this.properties = properties;
        this.verificationRepository = verificationRepository;
        this.identityCheckService = identityCheckService;
        this.objectMapper = objectMapper;
    }

    /**
     * 发起身份核验（人脸识别）。
     * <p>
     * 调用腾讯电子签 DetectIdentityFace API，返回人脸识别所需的参数。
     *
     * @param contractId 合同 ID
     * @param userId     用户 ID
     * @return 人脸识别参数 JSON
     */
    @Transactional
    public ObjectNode initiateVerification(String contractId, Long userId) {
        log.info("发起身份核验 [contractId={}, userId={}]", contractId, userId);

        // 1. 强制检查 KYC 状态
        IdentityCheckService.KycStatusInfo kycInfo = identityCheckService.requireKycVerified(userId);

        // 2. 检查当日重试限制
        checkRetryLimit(userId);

        // 3. 创建核验记录
        ContractIdentityVerification verification = ContractIdentityVerification.create(
                contractId, userId, kycInfo.kycRecordId(), VerificationType.FACE);
        verificationRepository.save(verification);

        // 4. 调用腾讯电子签身份核验 API
        long start = System.currentTimeMillis();
        try {
            TreeMap<String, Object> params = new TreeMap<>();
            params.put("Operator", buildOperator());

            // 签署人信息（从 KYC 获取）
            ObjectNode userInfo = objectMapper.createObjectNode();
            userInfo.put("Name", kycInfo.realName());
            userInfo.put("IdCardType", "ID_CARD");
            userInfo.put("IdCardNumber", kycInfo.idCardMask());
            params.put("UserInfo", userInfo);

            // 核验类型：人脸识别
            params.put("VerificationType", 1); // 1=人脸核身

            JsonNode response = apiClient.invoke("DetectIdentityFace", params);

            String verificationId = response.has("VerificationId")
                    ? response.get("VerificationId").asText() : null;

            // 5. 更新核验记录
            verification.startVerification(verificationId);
            verificationRepository.save(verification);

            log.info("身份核验已发起 [contractId={}, verificationId={}]",
                    contractId, verificationId);

            // 6. 返回人脸识别参数
            ObjectNode result = objectMapper.createObjectNode();
            result.put("code", 0);
            result.put("verificationId", verificationId);
            result.put("verificationType", "FACE");
            result.put("message", "请进行人脸识别");
            if (response.has("FaceUrl")) {
                result.put("faceUrl", response.get("FaceUrl").asText());
            }
            if (response.has("OrderId")) {
                result.put("orderId", response.get("OrderId").asText());
            }
            return result;

        } catch (Exception e) {
            verification.fail("发起核验失败: " + e.getMessage());
            verificationRepository.save(verification);

            log.error("发起身份核验失败 [contractId={}, userId={}]: {}",
                    contractId, userId, e.getMessage());
            throw e;
        }
    }

    /**
     * 处理身份核验回调。
     * <p>
     * 腾讯电子签在完成人脸识别后回调此方法，更新核验结果。
     *
     * @param verificationId  腾讯电子签核验 ID
     * @param passed          是否通过
     * @param faceScore       人脸比对分数
     * @param failureReason   失败原因（如果失败）
     */
    @Transactional
    public void handleVerificationCallback(String verificationId, boolean passed,
                                            java.math.BigDecimal faceScore,
                                            String failureReason) {
        log.info("处理身份核验回调 [verificationId={}, passed={}]", verificationId, passed);

        ContractIdentityVerification verification = verificationRepository
                .findByEssVerificationId(verificationId)
                .orElseThrow(() -> new IllegalStateException(
                        "核验记录不存在: " + verificationId));

        if (passed) {
            verification.pass(faceScore);
            log.info("身份核验通过 [contractId={}, faceScore={}]",
                    verification.getContractId(), faceScore);
        } else {
            verification.fail(failureReason);
            log.warn("身份核验失败 [contractId={}, reason={}]",
                    verification.getContractId(), failureReason);
        }

        verificationRepository.save(verification);
    }

    /**
     * 查询核验状态。
     *
     * @param contractId 合同 ID
     * @param userId     用户 ID
     * @return 核验状态信息
     */
    @Transactional(readOnly = true)
    public ObjectNode getVerificationStatus(String contractId, Long userId) {
        var opt = verificationRepository
                .findTopByContractIdAndUserIdOrderByCreatedAtDesc(contractId, userId);

        ObjectNode result = objectMapper.createObjectNode();
        if (opt.isEmpty()) {
            result.put("code", 0);
            result.put("status", "NONE");
            result.put("message", "无核验记录");
            return result;
        }

        ContractIdentityVerification v = opt.get();
        result.put("code", 0);
        result.put("status", v.getStatus().name());
        result.put("verificationType", v.getVerificationType().name());
        result.put("retryCount", v.getRetryCount());

        if (v.getFaceScore() != null) {
            result.put("faceScore", v.getFaceScore());
        }
        if (v.getVerifiedAt() != null) {
            result.put("verifiedAt", v.getVerifiedAt().toString());
        }
        if (v.getFailureReason() != null) {
            result.put("failureReason", v.getFailureReason());
        }

        return result;
    }

    /**
     * 检查指定合同的用户是否已通过身份核验。
     * <p>
     * 用于签署前置校验拦截器。
     *
     * @param contractId 合同 ID
     * @param userId     用户 ID
     * @return 是否通过
     */
    @Transactional(readOnly = true)
    public boolean isIdentityVerified(String contractId, Long userId) {
        return verificationRepository
                .findTopByContractIdAndUserIdOrderByCreatedAtDesc(contractId, userId)
                .map(v -> v.getStatus() == ContractIdentityVerification.Status.PASSED)
                .orElse(false);
    }

    /**
     * 检查当日重试限制。
     */
    private void checkRetryLimit(Long userId) {
        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        long count = verificationRepository.countByUserIdAndCreatedAtAfter(userId, todayStart);
        if (count >= MAX_RETRIES_PER_DAY) {
            throw new IllegalStateException(
                    String.format("今日身份核验次数已达上限（%d次），请明天再试", MAX_RETRIES_PER_DAY));
        }
    }

    private ObjectNode buildOperator() {
        ObjectNode operator = objectMapper.createObjectNode();
        operator.put("OperatorId", properties.operatorId());
        operator.put("OperatorType", 1);
        return operator;
    }
}
