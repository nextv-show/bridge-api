package com.sanshuiyuan.ess.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanshuiyuan.ess.config.ContractTemplateDataInitializer;
import com.sanshuiyuan.ess.domain.Contract;
import com.sanshuiyuan.ess.domain.ContractSnBinding;
import com.sanshuiyuan.ess.domain.ContractTemplate;
import com.sanshuiyuan.ess.infra.repository.ContractRepository;
import com.sanshuiyuan.ess.infra.repository.ContractSnBindingRepository;
import com.sanshuiyuan.ess.service.ContractGenerationService.GenerateContractRequest;
import com.sanshuiyuan.ess.service.ContractGenerationService.GenerateContractResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sanshuiyuan.ess.domain.ContractAuditTrail;

/**
 * T17.15: ContractGenerationService 测试 —— SN 预占位绑定逻辑。
 * 024: 更新为统一合同模板，移除附件相关断言。
 */
@ExtendWith(MockitoExtension.class)
class ContractGenerationServiceTest {

    @Mock private ContractTemplateService templateService;
    @Mock private ContractNoGenerator contractNoGenerator;
    @Mock private ContractRepository contractRepository;
    @Mock private ContractSnBindingRepository snBindingRepository;
    @Mock private AuditTrailService auditTrailService;
    private ObjectMapper objectMapper;
    private ContractGenerationService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new ContractGenerationService(
                templateService, contractNoGenerator, contractRepository,
                snBindingRepository, objectMapper, auditTrailService);
    }

    private ContractTemplate mockTemplate(String code, String content) {
        ContractTemplate tpl = mock(ContractTemplate.class);
        lenient().when(tpl.getId()).thenReturn(1L);
        lenient().when(tpl.getContentBody()).thenReturn(content);
        return tpl;
    }

    private GenerateContractRequest sampleRequest(String deviceSn) {
        return new GenerateContractRequest(
                100L, "ORD-001", deviceSn, "SSY-MINI-1",
                "29800", "张三", "110101199001011234", "13800138000");
    }

    @Test
    void generateContract_withDeviceSn_shouldCreateSnBinding() {
        // Arrange
        ContractTemplate mainTpl = mockTemplate("MAIN", "合同编号: {{contractNo}}, SN: {{deviceSn}}");

        when(templateService.getLatestVersion(ContractTemplateDataInitializer.MAIN_CONTRACT_CODE))
                .thenReturn(mainTpl);
        when(contractNoGenerator.generate()).thenReturn("CT-20260527-ABC123");
        when(contractRepository.save(any(Contract.class))).thenAnswer(inv -> inv.getArgument(0));
        when(snBindingRepository.save(any(ContractSnBinding.class))).thenAnswer(inv -> inv.getArgument(0));

        GenerateContractRequest request = sampleRequest("SN-DEVICE-001");

        // Act
        GenerateContractResult result = service.generateContract(request);

        // Assert: SN binding was saved
        ArgumentCaptor<ContractSnBinding> bindingCaptor = ArgumentCaptor.forClass(ContractSnBinding.class);
        verify(snBindingRepository).save(bindingCaptor.capture());

        ContractSnBinding binding = bindingCaptor.getValue();
        assertEquals("SN-DEVICE-001", binding.getDeviceSn());
        assertEquals(ContractSnBinding.BindingType.PRE_ALLOCATED, binding.getBindingType());

        // Assert: result contains SN in content
        assertNotNull(result);
        assertTrue(result.mainContractContent().contains("SN-DEVICE-001"));
        // attachmentContent is deprecated, always returns null
        assertNull(result.attachmentContent());
        assertEquals(Contract.ContractStatus.GENERATED, result.status());
    }

    @Test
    void generateContract_withoutDeviceSn_shouldNotCreateSnBinding() {
        // Arrange
        ContractTemplate mainTpl = mockTemplate("MAIN", "合同编号: {{contractNo}}, SN: {{deviceSn}}");

        when(templateService.getLatestVersion(ContractTemplateDataInitializer.MAIN_CONTRACT_CODE))
                .thenReturn(mainTpl);
        when(contractNoGenerator.generate()).thenReturn("CT-20260527-XYZ789");
        when(contractRepository.save(any(Contract.class))).thenAnswer(inv -> inv.getArgument(0));

        GenerateContractRequest request = sampleRequest(null);

        // Act
        GenerateContractResult result = service.generateContract(request);

        // Assert: SN binding was NOT saved
        verify(snBindingRepository, never()).save(any());
        assertNotNull(result);
        // deviceSn defaults to "待分配"
        assertTrue(result.mainContractContent().contains("待分配"));
    }

    @Test
    void generateContract_withBlankDeviceSn_shouldNotCreateSnBinding() {
        // Arrange
        ContractTemplate mainTpl = mockTemplate("MAIN", "SN: {{deviceSn}}");

        when(templateService.getLatestVersion(ContractTemplateDataInitializer.MAIN_CONTRACT_CODE))
                .thenReturn(mainTpl);
        when(contractNoGenerator.generate()).thenReturn("CT-20260527-BLK000");
        when(contractRepository.save(any(Contract.class))).thenAnswer(inv -> inv.getArgument(0));

        GenerateContractRequest request = sampleRequest("   ");

        // Act
        service.generateContract(request);

        // Assert: SN binding was NOT saved (blank SN)
        verify(snBindingRepository, never()).save(any());
    }

    @Test
    void generateContract_shouldFillAllTemplateFields() {
        // Arrange
        String templateContent = "编号:{{contractNo}} 日期:{{signDate}} 姓名:{{userName}} " +
                "身份证:{{idCardNo}} 电话:{{phone}} 型号:{{deviceModel}} SN:{{deviceSn}} 价格:{{devicePrice}}";
        ContractTemplate mainTpl = mockTemplate("MAIN", templateContent);

        when(templateService.getLatestVersion(ContractTemplateDataInitializer.MAIN_CONTRACT_CODE))
                .thenReturn(mainTpl);
        when(contractNoGenerator.generate()).thenReturn("CT-20260527-FULL01");
        when(contractRepository.save(any(Contract.class))).thenAnswer(inv -> inv.getArgument(0));
        when(snBindingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GenerateContractRequest request = sampleRequest("SN-FULL-TEST");

        // Act
        GenerateContractResult result = service.generateContract(request);

        // Assert: all fields filled
        assertTrue(result.mainContractContent().contains("CT-20260527-FULL01"));
        assertTrue(result.mainContractContent().contains("张三"));
        assertTrue(result.mainContractContent().contains("110101199001011234"));
        assertTrue(result.mainContractContent().contains("13800138000"));
        assertTrue(result.mainContractContent().contains("SSY-MINI-1"));
        assertTrue(result.mainContractContent().contains("SN-FULL-TEST"));
        assertTrue(result.mainContractContent().contains("29800"));
    }

    @Test
    void generateContract_contractIdPassedToSnBinding() {
        // Arrange - simulate JPA assigning an ID
        ContractTemplate mainTpl = mockTemplate("MAIN", "test {{contractNo}}");

        when(templateService.getLatestVersion(anyString())).thenReturn(mainTpl);
        when(contractNoGenerator.generate()).thenReturn("CT-20260527-IDCHECK");

        // First save (draft) returns contract with id=99
        when(contractRepository.save(any(Contract.class))).thenAnswer(inv -> {
            Contract c = inv.getArgument(0);
            // Simulate JPA ID assignment via reflection for test
            try {
                var field = Contract.class.getDeclaredField("id");
                field.setAccessible(true);
                field.set(c, 99L);
            } catch (Exception e) { /* ignore */ }
            return c;
        });
        when(snBindingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GenerateContractRequest request = sampleRequest("SN-ID-CHECK");

        // Act
        service.generateContract(request);

        // Assert: SN binding uses contract ID 99
        ArgumentCaptor<ContractSnBinding> captor = ArgumentCaptor.forClass(ContractSnBinding.class);
        verify(snBindingRepository).save(captor.capture());
        assertEquals(99L, captor.getValue().getContractId());
        assertEquals("SN-ID-CHECK", captor.getValue().getDeviceSn());
    }

    @Test
    void generateContract_kycAuthPurpose_usesKycTemplateAndSkipsSnBinding() {
        // Arrange: KYC_AUTH 用途选用实名承诺书模板，且无设备 SN。
        ContractTemplate kycTpl = mockTemplate("KYC", "承诺书编号:{{contractNo}} 承诺人:{{userName}}");
        when(templateService.getLatestVersion(ContractTemplateDataInitializer.KYC_AUTH_CONTRACT_CODE))
                .thenReturn(kycTpl);
        when(contractNoGenerator.generate()).thenReturn("CT-KYC-001");
        when(contractRepository.save(any(Contract.class))).thenAnswer(inv -> inv.getArgument(0));

        GenerateContractRequest request = new GenerateContractRequest(
                100L, "", null, null, null, "张三", "110101199001011234", "13800138000",
                ContractGenerationService.ContractPurpose.KYC_AUTH);

        // Act
        GenerateContractResult result = service.generateContract(request);

        // Assert: 选用 KYC 模板，未取设备主合同模板
        verify(templateService).getLatestVersion(ContractTemplateDataInitializer.KYC_AUTH_CONTRACT_CODE);
        verify(templateService, never())
                .getLatestVersion(ContractTemplateDataInitializer.MAIN_CONTRACT_CODE);
        // 无 deviceSn → 不建 SN 绑定
        verify(snBindingRepository, never()).save(any());
        // 实名承诺正文填充成功（设备占位符缺省为空串不致 NPE）
        assertTrue(result.mainContractContent().contains("张三"));
        assertTrue(result.mainContractContent().contains("CT-KYC-001"));
        assertEquals(Contract.ContractStatus.GENERATED, result.status());
    }

    @Test
    void generateContract_kycAuth_ignoresInjectedDeviceFields() {
        // Arrange: 攻击者绕过 cend，直接对 KYC_AUTH 注入设备/订单字段，企图预占任意 SN。
        ContractTemplate kycTpl = mockTemplate("KYC",
                "承诺人:{{userName}} 型号:{{deviceModel}} SN:{{deviceSn}} 价格:{{devicePrice}}");
        when(templateService.getLatestVersion(ContractTemplateDataInitializer.KYC_AUTH_CONTRACT_CODE))
                .thenReturn(kycTpl);
        when(contractNoGenerator.generate()).thenReturn("CT-KYC-INJ");
        when(contractRepository.save(any(Contract.class))).thenAnswer(inv -> inv.getArgument(0));

        GenerateContractRequest tainted = new GenerateContractRequest(
                100L, "ORD-HACK", "HACK-SN-001", "HACK-MODEL", "99999",
                "张三", "110101199001011234", "13800138000",
                ContractGenerationService.ContractPurpose.KYC_AUTH);

        // Act
        GenerateContractResult result = service.generateContract(tainted);

        // Assert: 绝不建立 SN 预占位绑定
        verify(snBindingRepository, never()).save(any());

        // Assert: 合同正文不含任何注入的设备/订单值
        String content = result.mainContractContent();
        assertFalse(content.contains("HACK-SN-001"), "deviceSn 注入值不应出现");
        assertFalse(content.contains("HACK-MODEL"), "deviceModel 注入值不应出现");
        assertFalse(content.contains("99999"), "devicePrice 注入值不应出现");
        assertTrue(content.contains("张三"));

        // Assert: 草稿合同的 deviceSn/orderId 被清空，contract_fields_json 不含假设备值
        ArgumentCaptor<Contract> contractCaptor = ArgumentCaptor.forClass(Contract.class);
        verify(contractRepository, atLeastOnce()).save(contractCaptor.capture());
        Contract saved = contractCaptor.getAllValues().get(contractCaptor.getAllValues().size() - 1);
        assertNull(saved.getDeviceSn(), "KYC_AUTH 草稿 deviceSn 应为 null");
        assertEquals("", saved.getOrderId(), "KYC_AUTH 草稿 orderId 应被清空");
        String fieldsJson = saved.getContractFieldsJson();
        assertNotNull(fieldsJson);
        assertFalse(fieldsJson.contains("HACK-SN-001"), "字段 JSON 不应含注入 deviceSn");
        assertFalse(fieldsJson.contains("HACK-MODEL"), "字段 JSON 不应含注入 deviceModel");
        assertFalse(fieldsJson.contains("99999"), "字段 JSON 不应含注入 devicePrice");
    }

    @Test
    void generateContract_shouldTransitionStatusDraftToGenerated() {
        // Arrange
        ContractTemplate mainTpl = mockTemplate("MAIN", "test");

        when(templateService.getLatestVersion(ContractTemplateDataInitializer.MAIN_CONTRACT_CODE))
                .thenReturn(mainTpl);
        when(contractNoGenerator.generate()).thenReturn("CT-20260527-STAT01");
        when(contractRepository.save(any(Contract.class))).thenAnswer(inv -> inv.getArgument(0));
        when(snBindingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        GenerateContractResult result = service.generateContract(sampleRequest("SN-STATUS"));

        // Assert
        assertEquals(Contract.ContractStatus.GENERATED, result.status());
    }
}
