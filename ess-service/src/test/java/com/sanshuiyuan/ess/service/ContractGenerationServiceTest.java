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
        ContractTemplate attachTpl = mockTemplate("ATTACH", "SN: {{deviceSn}}");

        when(templateService.getLatestVersion(ContractTemplateDataInitializer.MAIN_CONTRACT_CODE))
                .thenReturn(mainTpl);
        when(templateService.getLatestVersion(ContractTemplateDataInitializer.PROPERTY_CERT_CODE))
                .thenReturn(attachTpl);
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
        assertTrue(result.attachmentContent().contains("SN-DEVICE-001"));
        assertEquals(Contract.ContractStatus.GENERATED, result.status());
    }

    @Test
    void generateContract_withoutDeviceSn_shouldNotCreateSnBinding() {
        // Arrange
        ContractTemplate mainTpl = mockTemplate("MAIN", "合同编号: {{contractNo}}, SN: {{deviceSn}}");
        ContractTemplate attachTpl = mockTemplate("ATTACH", "SN: {{deviceSn}}");

        when(templateService.getLatestVersion(ContractTemplateDataInitializer.MAIN_CONTRACT_CODE))
                .thenReturn(mainTpl);
        when(templateService.getLatestVersion(ContractTemplateDataInitializer.PROPERTY_CERT_CODE))
                .thenReturn(attachTpl);
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
        ContractTemplate attachTpl = mockTemplate("ATTACH", "SN: {{deviceSn}}");

        when(templateService.getLatestVersion(ContractTemplateDataInitializer.MAIN_CONTRACT_CODE))
                .thenReturn(mainTpl);
        when(templateService.getLatestVersion(ContractTemplateDataInitializer.PROPERTY_CERT_CODE))
                .thenReturn(attachTpl);
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
        ContractTemplate attachTpl = mockTemplate("ATTACH", templateContent);

        when(templateService.getLatestVersion(ContractTemplateDataInitializer.MAIN_CONTRACT_CODE))
                .thenReturn(mainTpl);
        when(templateService.getLatestVersion(ContractTemplateDataInitializer.PROPERTY_CERT_CODE))
                .thenReturn(attachTpl);
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
        ContractTemplate attachTpl = mockTemplate("ATTACH", "test {{contractNo}}");

        when(templateService.getLatestVersion(anyString())).thenReturn(mainTpl);
        lenient().when(templateService.getLatestVersion(ContractTemplateDataInitializer.PROPERTY_CERT_CODE))
                .thenReturn(attachTpl);
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
    void generateContract_shouldTransitionStatusDraftToGenerated() {
        // Arrange
        ContractTemplate mainTpl = mockTemplate("MAIN", "test");
        ContractTemplate attachTpl = mockTemplate("ATTACH", "test");

        when(templateService.getLatestVersion(ContractTemplateDataInitializer.MAIN_CONTRACT_CODE))
                .thenReturn(mainTpl);
        when(templateService.getLatestVersion(ContractTemplateDataInitializer.PROPERTY_CERT_CODE))
                .thenReturn(attachTpl);
        when(contractNoGenerator.generate()).thenReturn("CT-20260527-STAT01");
        when(contractRepository.save(any(Contract.class))).thenAnswer(inv -> inv.getArgument(0));
        when(snBindingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        GenerateContractResult result = service.generateContract(sampleRequest("SN-STATUS"));

        // Assert
        assertEquals(Contract.ContractStatus.GENERATED, result.status());
    }
}
