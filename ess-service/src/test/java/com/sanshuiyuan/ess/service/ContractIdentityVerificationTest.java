package com.sanshuiyuan.ess.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanshuiyuan.ess.config.EssProperties;
import com.sanshuiyuan.ess.config.SigningPreCheckInterceptor;
import com.sanshuiyuan.ess.domain.ContractIdentityVerification;
import com.sanshuiyuan.ess.domain.ContractIdentityVerification.Status;
import com.sanshuiyuan.ess.domain.ContractIdentityVerification.VerificationType;
import com.sanshuiyuan.ess.domain.EssFlowRecord;
import com.sanshuiyuan.ess.domain.FlowStatus;
import com.sanshuiyuan.ess.exception.EssFlowException;
import com.sanshuiyuan.ess.infra.client.EssApiClient;
import com.sanshuiyuan.ess.infra.repository.ContractIdentityVerificationRepository;
import com.sanshuiyuan.ess.infra.repository.EssFlowRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * T18.13: 端到端测试 - KYC → 身份核验通过 → 签署放行；失败阻断 + 重试
 */
@ExtendWith(MockitoExtension.class)
class ContractIdentityVerificationTest {

    @Mock private EssApiClient apiClient;
    @Mock private EssFlowRecordRepository flowRecordRepository;
    @Mock private ContractIdentityVerificationRepository verificationRepository;
    @Mock private EssApiLogService apiLogService;

    private EssProperties properties;
    private ObjectMapper objectMapper;
    private IdentityCheckService identityCheckService;
    private IdentityVerificationService verificationService;
    private SigningPreCheckInterceptor signingPreCheck;
    private EssContractService contractService;

    @BeforeEach
    void setUp() {
        properties = new EssProperties("sid", "skey", "op-001", "corp-001",
                "tpl-001", "https://cb.example.com", null, null, 5000, 10000, 3);
        objectMapper = new ObjectMapper();
        identityCheckService = new IdentityCheckService(objectMapper);
        verificationService = new IdentityVerificationService(
                apiClient, properties, verificationRepository,
                identityCheckService, objectMapper);
        signingPreCheck = new SigningPreCheckInterceptor(verificationRepository, flowRecordRepository);
        contractService = new EssContractService(
                apiClient, properties, flowRecordRepository,
                apiLogService, objectMapper, signingPreCheck);
    }

    @Nested
    @DisplayName("场景1: KYC → 身份核验通过 → 签署放行")
    class HappyPathTests {

        @Test
        @DisplayName("完整流程: 创建流程 → 身份核验通过 → 启动签署")
        void fullHappyPath_createFlow_verifyIdentity_startSigning() {
            String contractId = "contract-001";
            Long userId = 1001L;

            // Step 1: 创建签署流程
            when(flowRecordRepository.findByContractId(contractId)).thenReturn(Optional.empty());
            when(flowRecordRepository.save(any(EssFlowRecord.class))).thenAnswer(inv -> inv.getArgument(0));

            ObjectNode createResponse = objectMapper.createObjectNode();
            createResponse.put("FlowId", "flow-ess-001");
            when(apiClient.invoke(eq("CreateFlow"), any())).thenReturn(createResponse);

            EssFlowRecord flowRecord = contractService.createFlow(contractId, "测试合同", "[{}]");
            assertNotNull(flowRecord);
            assertEquals("flow-ess-001", flowRecord.getEssFlowId());

            // Step 2: 发起身份核验
            when(verificationRepository.countByUserIdAndCreatedAtAfter(eq(userId), any(LocalDateTime.class)))
                    .thenReturn(0L);
            when(verificationRepository.save(any(ContractIdentityVerification.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            ObjectNode detectResponse = objectMapper.createObjectNode();
            detectResponse.put("VerificationId", "verify-ess-001");
            detectResponse.put("FaceUrl", "https://face.example.com/token123");
            when(apiClient.invoke(eq("DetectIdentityFace"), any())).thenReturn(detectResponse);

            ObjectNode verifyResult = verificationService.initiateVerification(contractId, userId);
            assertEquals(0, verifyResult.get("code").asInt());
            assertEquals("verify-ess-001", verifyResult.get("verificationId").asText());

            // Step 3: 模拟核验通过回调
            ContractIdentityVerification verification = ContractIdentityVerification.create(
                    contractId, userId, "kyc-record-1001", VerificationType.FACE);
            verification.startVerification("verify-ess-001");
            when(verificationRepository.findByEssVerificationId("verify-ess-001"))
                    .thenReturn(Optional.of(verification));

            verificationService.handleVerificationCallback(
                    "verify-ess-001", true, new BigDecimal("95.50"), null);

            assertEquals(Status.PASSED, verification.getStatus());
            assertEquals(new BigDecimal("95.50"), verification.getFaceScore());

            // Step 4: 启动签署（带身份核验校验）
            flowRecord.assignFlowId("flow-ess-001");
            when(flowRecordRepository.findByContractId(contractId))
                    .thenReturn(Optional.of(flowRecord));
            when(verificationRepository.findTopByContractIdAndUserIdOrderByCreatedAtDesc(
                    contractId, userId)).thenReturn(Optional.of(verification));

            ObjectNode startResponse = objectMapper.createObjectNode();
            when(apiClient.invoke(eq("StartFlow"), any())).thenReturn(startResponse);

            // 不应抛异常
            assertDoesNotThrow(() -> contractService.startFlow(contractId, userId));
        }
    }

    @Nested
    @DisplayName("场景2: 身份核验失败 → 阻断签署")
    class FailureBlockTests {

        @Test
        @DisplayName("身份未核验时启动签署应被阻断")
        void startFlow_withoutIdentityVerification_shouldBlock() {
            String contractId = "contract-002";
            Long userId = 2001L;

            EssFlowRecord flowRecord = EssFlowRecord.create(contractId, "[{}]");
            flowRecord.assignFlowId("flow-ess-002");
            when(flowRecordRepository.findByContractId(contractId))
                    .thenReturn(Optional.of(flowRecord));
            when(verificationRepository.findTopByContractIdAndUserIdOrderByCreatedAtDesc(
                    contractId, userId)).thenReturn(Optional.empty());

            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> contractService.startFlow(contractId, userId));

            assertTrue(exception.getMessage().contains("未进行身份核验"));
        }

        @Test
        @DisplayName("身份核验失败时启动签署应被阻断")
        void startFlow_withFailedVerification_shouldBlock() {
            String contractId = "contract-003";
            Long userId = 3001L;

            EssFlowRecord flowRecord = EssFlowRecord.create(contractId, "[{}]");
            flowRecord.assignFlowId("flow-ess-003");
            when(flowRecordRepository.findByContractId(contractId))
                    .thenReturn(Optional.of(flowRecord));

            ContractIdentityVerification verification = ContractIdentityVerification.create(
                    contractId, userId, "kyc-3001", VerificationType.FACE);
            verification.fail("人脸比对分数过低");
            when(verificationRepository.findTopByContractIdAndUserIdOrderByCreatedAtDesc(
                    contractId, userId)).thenReturn(Optional.of(verification));

            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> contractService.startFlow(contractId, userId));

            assertTrue(exception.getMessage().contains("身份核验未通过"));
        }

        @Test
        @DisplayName("核验进行中时启动签署应被阻断")
        void startFlow_withInProgressVerification_shouldBlock() {
            String contractId = "contract-004";
            Long userId = 4001L;

            EssFlowRecord flowRecord = EssFlowRecord.create(contractId, "[{}]");
            flowRecord.assignFlowId("flow-ess-004");
            when(flowRecordRepository.findByContractId(contractId))
                    .thenReturn(Optional.of(flowRecord));

            ContractIdentityVerification verification = ContractIdentityVerification.create(
                    contractId, userId, "kyc-4001", VerificationType.FACE);
            verification.startVerification("verify-in-progress");
            when(verificationRepository.findTopByContractIdAndUserIdOrderByCreatedAtDesc(
                    contractId, userId)).thenReturn(Optional.of(verification));

            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> contractService.startFlow(contractId, userId));

            assertTrue(exception.getMessage().contains("身份核验进行中"));
        }
    }

    @Nested
    @DisplayName("场景3: 重试限制")
    class RetryLimitTests {

        @Test
        @DisplayName("每日超过3次核验应被拒绝")
        void retryLimitExceeded_shouldThrow() {
            Long userId = 5001L;

            // 模拟已有3次核验记录
            when(verificationRepository.countByUserIdAndCreatedAtAfter(eq(userId), any(LocalDateTime.class)))
                    .thenReturn(3L);

            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> verificationService.initiateVerification("contract-005", userId));

            assertTrue(exception.getMessage().contains("已达上限"));
        }

        @Test
        @DisplayName("失败后记录重试次数和原因")
        void failureShouldRecordRetryCountAndReason() {
            ContractIdentityVerification verification = ContractIdentityVerification.create(
                    "contract-006", 6001L, "kyc-6001", VerificationType.FACE);

            assertEquals(0, verification.getRetryCount());

            verification.fail("人脸比对分数过低");
            assertEquals(1, verification.getRetryCount());
            assertEquals("人脸比对分数过低", verification.getFailureReason());

            verification.fail("活体检测未通过");
            assertEquals(2, verification.getRetryCount());
            assertEquals("活体检测未通过", verification.getFailureReason());
        }
    }

    @Nested
    @DisplayName("场景4: 核验状态查询")
    class StatusQueryTests {

        @Test
        @DisplayName("无核验记录应返回 NONE")
        void noRecord_shouldReturnNone() {
            when(verificationRepository.findTopByContractIdAndUserIdOrderByCreatedAtDesc(
                    "contract-007", 7001L)).thenReturn(Optional.empty());

            ObjectNode result = verificationService.getVerificationStatus("contract-007", 7001L);

            assertEquals("NONE", result.get("status").asText());
        }

        @Test
        @DisplayName("已通过应返回 PASSED + faceScore")
        void passedRecord_shouldReturnPassedWithScore() {
            ContractIdentityVerification verification = ContractIdentityVerification.create(
                    "contract-008", 8001L, "kyc-8001", VerificationType.FACE);
            verification.startVerification("verify-008");
            verification.pass(new BigDecimal("98.75"));

            when(verificationRepository.findTopByContractIdAndUserIdOrderByCreatedAtDesc(
                    "contract-008", 8001L)).thenReturn(Optional.of(verification));

            ObjectNode result = verificationService.getVerificationStatus("contract-008", 8001L);

            assertEquals("PASSED", result.get("status").asText());
            assertEquals("98.75", result.get("faceScore").asText());
        }
    }
}
