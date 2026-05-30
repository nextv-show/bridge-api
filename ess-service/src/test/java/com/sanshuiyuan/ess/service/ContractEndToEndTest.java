package com.sanshuiyuan.ess.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanshuiyuan.ess.domain.Contract;
import com.sanshuiyuan.ess.domain.Contract.ContractStatus;
import com.sanshuiyuan.ess.domain.ContractSnBinding;
import com.sanshuiyuan.ess.domain.ContractSnBinding.BindingType;
import com.sanshuiyuan.ess.domain.ContractTemplate;
import com.sanshuiyuan.ess.infra.repository.ContractRepository;
import com.sanshuiyuan.ess.infra.repository.ContractSnBindingRepository;
import com.sanshuiyuan.ess.infra.repository.ContractTemplateRepository;
import com.sanshuiyuan.ess.service.ContractGenerationService.GenerateContractRequest;
import com.sanshuiyuan.ess.service.ContractGenerationService.GenerateContractResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * T17.20: 端到端集成测试 —— 合同生成 → 签署发起 → 回调完成 → SN绑定确认。
 * <p>
 * 验证完整流程中各服务之间的协作是否正确：
 * 1. 合同生成（模板填充 + SN 预占位）
 * 2. 状态机流转（DRAFT → GENERATED → SIGNING → SIGNED）
 * 3. 签署完成回调（SN PRE_ALLOCATED → CONFIRMED）
 */
@ExtendWith(MockitoExtension.class)
class ContractEndToEndTest {

    @Mock private ContractTemplateService templateService;
    @Mock private ContractNoGenerator contractNoGenerator;
    @Mock private ContractRepository contractRepository;
    @Mock private ContractSnBindingRepository snBindingRepository;
    @Mock private EssContractService essContractService;
    @Mock private ContractTemplateRepository templateRepository;
    @Mock private ContractArchiveService archiveService;
    @Mock private AuditTrailService auditTrailService;

    private ObjectMapper objectMapper;
    private ContractGenerationService generationService;
    private ContractStateMachineService stateMachineService;
    private ContractSigningService signingService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        generationService = new ContractGenerationService(
                templateService, contractNoGenerator, contractRepository,
                snBindingRepository, objectMapper, auditTrailService);

        stateMachineService = new ContractStateMachineService(contractRepository);

        signingService = new ContractSigningService(
                contractRepository, snBindingRepository,
                essContractService, stateMachineService, archiveService, auditTrailService,
                objectMapper);
    }

    /**
     * 完整端到端流程：生成 → 发起签署 → 回调完成。
     */
    @Test
    void fullContractLifecycle_generateToSigned() throws Exception {
        // ===== 阶段 1: 合同生成 =====
        ContractTemplate mainTpl = mock(ContractTemplate.class);
        when(mainTpl.getId()).thenReturn(1L);
        when(mainTpl.getContentBody()).thenReturn(
                "合同编号: {{contractNo}}, 姓名: {{userName}}, SN: {{deviceSn}}, 价格: {{devicePrice}}");

        when(templateService.getLatestVersion("MAIN_CONTRACT")).thenReturn(mainTpl);
        when(contractNoGenerator.generate()).thenReturn("CT-20260527-E2E001");

        // 模拟 JPA save：分配 ID 并返回
        when(contractRepository.save(any(Contract.class))).thenAnswer(inv -> {
            Contract c = inv.getArgument(0);
            try {
                var idField = Contract.class.getDeclaredField("id");
                idField.setAccessible(true);
                if (c.getId() == null) idField.set(c, 1L);
            } catch (Exception e) { /* ignore */ }
            return c;
        });
        when(snBindingRepository.save(any(ContractSnBinding.class))).thenAnswer(inv -> inv.getArgument(0));

        GenerateContractRequest genRequest = new GenerateContractRequest(
                100L, "ORD-E2E-001", "SN-E2E-DEVICE", "SSY-MINI-1",
                "29800", "李四", "320102199203022345", "13900139000");

        GenerateContractResult genResult = generationService.generateContract(genRequest);

        // 验证生成结果
        assertEquals(ContractStatus.GENERATED, genResult.status());
        assertEquals("CT-20260527-E2E001", genResult.contractNo());
        assertTrue(genResult.mainContractContent().contains("SN-E2E-DEVICE"));
        assertTrue(genResult.mainContractContent().contains("李四"));
        assertTrue(genResult.mainContractContent().contains("29800"));
        assertNull(genResult.attachmentContent());

        // 验证 SN 预占位
        ArgumentCaptor<ContractSnBinding> snCaptor = ArgumentCaptor.forClass(ContractSnBinding.class);
        verify(snBindingRepository).save(snCaptor.capture());
        assertEquals("SN-E2E-DEVICE", snCaptor.getValue().getDeviceSn());
        assertEquals(BindingType.PRE_ALLOCATED, snCaptor.getValue().getBindingType());

        // ===== 阶段 2: 发起签署 =====
        // 重置 mock 以模拟查找已保存的合同
        Contract savedContract = Contract.createDraft(
                "CT-20260527-E2E001", 1L, 100L, "ORD-E2E-001", "SN-E2E-DEVICE");
        savedContract.markGenerated(
                objectMapper.writeValueAsString(java.util.Map.of("deviceSn", "SN-E2E-DEVICE")),
                // signerInfoJson 必须含真实姓名，否则 ContractSigningService.validateRealName 会抛
                objectMapper.writeValueAsString(java.util.Map.of(
                        "userId", 100,
                        "userName", "李四",
                        "idCardNo", "320102199203022345",
                        "phone", "13900139000")));
        // 模拟 JPA ID
        try {
            var idField = Contract.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(savedContract, 1L);
        } catch (Exception e) { /* ignore */ }

        when(contractRepository.findById(1L)).thenReturn(java.util.Optional.of(savedContract));

        com.sanshuiyuan.ess.domain.EssFlowRecord flowRecord =
                com.sanshuiyuan.ess.domain.EssFlowRecord.create(
                        "CT-20260527-E2E001", savedContract.getSignerInfoJson());
        flowRecord.assignFlowId("flow-ess-e2e-001");
        when(essContractService.createFlow(eq("CT-20260527-E2E001"), anyString(), anyString()))
                .thenReturn(flowRecord);

        ContractSigningService.SigningInitiationResult signResult =
                signingService.initiateSigning(1L, 100L);

        assertEquals(ContractStatus.SIGNING, signResult.status());
        assertEquals("flow-ess-e2e-001", signResult.essFlowId());
        assertEquals("CT-20260527-E2E001", signResult.contractNo());

        // ===== 阶段 3: 签署完成回调 =====
        // 模拟 SN 预占位绑定
        ContractSnBinding preBinding = ContractSnBinding.preAllocate(1L, "SN-E2E-DEVICE");
        when(snBindingRepository.findByContractId(1L)).thenReturn(List.of(preBinding));

        signingService.completeSigning(1L, "https://pdf.example.com/contract.pdf", "hash-abc123");

        // 验证合同状态已更新为 SIGNED
        assertEquals(ContractStatus.SIGNED, savedContract.getStatus());
        assertEquals("https://pdf.example.com/contract.pdf", savedContract.getPdfUrl());
        assertEquals("hash-abc123", savedContract.getPdfHash());

        // 验证 SN 绑定已确认
        assertEquals(BindingType.CONFIRMED, preBinding.getBindingType());
        verify(snBindingRepository).save(preBinding);
    }

    /**
     * 测试非法状态流转被阻断。
     */
    @Test
    void illegalTransition_draftToSigning_shouldFail() {
        Contract draftContract = Contract.createDraft("CT-TEST", 1L, 100L, "ORD", "SN-001");
        try {
            var idField = Contract.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(draftContract, 1L);
        } catch (Exception e) { /* ignore */ }

        when(contractRepository.findById(1L)).thenReturn(java.util.Optional.of(draftContract));

        // DRAFT -> SIGNING 是非法的（必须先经过 GENERATED）
        assertThrows(IllegalStateException.class,
                () -> signingService.initiateSigning(1L, 100L));
    }

    /**
     * 测试 SN 预占位 → 确认的完整流程。
     */
    @Test
    void snBindingLifecycle_preAllocateToConfirmed() {
        ContractSnBinding binding = ContractSnBinding.preAllocate(1L, "SN-LIFECYCLE");

        assertEquals(BindingType.PRE_ALLOCATED, binding.getBindingType());

        binding.confirm();
        assertEquals(BindingType.CONFIRMED, binding.getBindingType());
    }

    /**
     * 测试 SN 预占位 → 释放的流程。
     */
    @Test
    void snBindingLifecycle_preAllocateToReleased() {
        ContractSnBinding binding = ContractSnBinding.preAllocate(1L, "SN-RELEASE");

        assertEquals(BindingType.PRE_ALLOCATED, binding.getBindingType());

        binding.release();
        assertEquals(BindingType.RELEASED, binding.getBindingType());
    }
}
