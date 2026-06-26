package com.sanshuiyuan.ess.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanshuiyuan.ess.config.EssFileProperties;
import com.sanshuiyuan.ess.config.EssProperties;
import com.sanshuiyuan.ess.config.SigningPreCheckInterceptor;
import com.sanshuiyuan.ess.domain.EssFlowRecord;
import com.sanshuiyuan.ess.domain.FlowStatus;
import com.sanshuiyuan.ess.exception.EssFlowException;
import com.sanshuiyuan.ess.infra.client.EssApiClient;
import com.sanshuiyuan.ess.infra.repository.EssFlowRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EssContractServiceTest {

    @Mock private EssApiClient apiClient;
    @Mock private EssFlowRecordRepository flowRecordRepository;
    @Mock private EssApiLogService apiLogService;
    @Mock private SigningPreCheckInterceptor signingPreCheck;
    private EssProperties properties;
    private ObjectMapper objectMapper;
    private EssContractService service;

    @BeforeEach
    void setUp() {
        properties = new EssProperties("sid", "skey", "op-001", "corp-001",
                "tpl-001", "https://cb.example.com", null, null, 5000, 10000, 3, Boolean.FALSE, "3");
        objectMapper = new ObjectMapper();
        service = new EssContractService(apiClient, properties, flowRecordRepository,
                apiLogService, objectMapper, signingPreCheck);
    }

    @Test
    void createFlow_shouldCreateRecordAndCallApi() {
        // Arrange
        when(flowRecordRepository.findByContractId("c-001")).thenReturn(Optional.empty());
        when(flowRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ObjectNode apiResponse = objectMapper.createObjectNode();
        apiResponse.put("FlowId", "flow-ess-001");
        when(apiClient.invoke(eq("CreateFlow"), any())).thenReturn(apiResponse);

        // Act
        EssFlowRecord result = service.createFlow("c-001", "测试合同", "[{}]");

        // Assert
        assertNotNull(result);
        assertEquals("flow-ess-001", result.getEssFlowId());
        assertEquals(FlowStatus.CREATED, result.getFlowStatus());
        verify(apiClient).invoke(eq("CreateFlow"), any());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void createFlowByFiles_uploadsThenCreatesFlowWithSignComponents() {
        // Arrange: 启用文件模式
        service.setFileProperties(new EssFileProperties(
                true, "file.test.ess.tencent.cn", "电子签字", "", false,
                "公章", "天津源创智能科技有限公司", "Right", 120.0, 44.0, 5.0, 0.0,
                "Below", 100.0, 100.0, 0.0, 5.0, null));

        when(flowRecordRepository.findByContractId("c-f1")).thenReturn(Optional.empty());
        when(flowRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ObjectNode uploadResp = objectMapper.createObjectNode();
        uploadResp.set("FileIds", objectMapper.createArrayNode().add("file-xyz"));
        when(apiClient.invoke(eq("UploadFiles"), any(), anyString())).thenReturn(uploadResp);

        ObjectNode flowResp = objectMapper.createObjectNode();
        flowResp.put("FlowId", "flow-file-1");
        when(apiClient.invoke(eq("CreateFlowByFiles"), any())).thenReturn(flowResp);

        ArgumentCaptor<TreeMap> paramsCaptor = ArgumentCaptor.forClass(TreeMap.class);

        // Act
        EssFlowRecord result = service.createFlowByFiles(
                "c-f1", "测试合同",
                "[{\"userName\":\"张三\",\"phone\":\"13800138000\"}]",
                new byte[]{1, 2, 3}, "c-f1.pdf");

        // Assert
        assertNotNull(result);
        assertEquals("flow-file-1", result.getEssFlowId());
        assertEquals(FlowStatus.CREATED, result.getFlowStatus());
        // 上传走文件专用域名
        verify(apiClient).invoke(eq("UploadFiles"), any(), eq("file.test.ess.tencent.cn"));
        verify(apiClient).invoke(eq("CreateFlowByFiles"), paramsCaptor.capture());

        TreeMap params = paramsCaptor.getValue();
        assertTrue(params.containsKey("FileIds"), "应带 FileIds");
        assertTrue(params.containsKey("Approvers"), "应带 Approvers");
        java.util.List<?> approvers = (java.util.List<?>) params.get("Approvers");
        TreeMap<String, Object> approver = (TreeMap<String, Object>) approvers.get(0);
        assertFalse(approver.containsKey("RecipientId"),
                "文件模式 approver 不应带 RecipientId（CreateFlowByFiles 会报 UnknownParameter）");
        assertTrue(approver.containsKey("SignComponents"), "签署方应挂签名控件");
        java.util.List<?> comps = (java.util.List<?>) approver.get("SignComponents");
        TreeMap<String, Object> sign = (TreeMap<String, Object>) comps.get(0);
        assertEquals("SIGN_SIGNATURE", sign.get("ComponentType"));
        assertEquals("KEYWORD", sign.get("GenerateMode"));
        assertEquals("电子签字", sign.get("ComponentId"), "关键字定位：关键字走 ComponentId");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void createFlowByFiles_withCompanySeal_appendsAutoSignSealApprover() {
        // 启用文件模式 + 乙方企业自动盖章（companySeal=true + 印章ID）
        service.setFileProperties(new EssFileProperties(
                true, "file.test.ess.tencent.cn", "电子签字", "", true,
                "公章", "天津源创智能科技有限公司", "Right", 120.0, 44.0, 5.0, 0.0,
                "Below", 100.0, 100.0, 0.0, 5.0, "seal-id-abc123"));

        when(flowRecordRepository.findByContractId("c-seal")).thenReturn(Optional.empty());
        when(flowRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ObjectNode uploadResp = objectMapper.createObjectNode();
        uploadResp.set("FileIds", objectMapper.createArrayNode().add("file-seal"));
        when(apiClient.invoke(eq("UploadFiles"), any(), anyString())).thenReturn(uploadResp);
        ObjectNode flowResp = objectMapper.createObjectNode();
        flowResp.put("FlowId", "flow-seal-1");
        when(apiClient.invoke(eq("CreateFlowByFiles"), any())).thenReturn(flowResp);

        ArgumentCaptor<TreeMap> paramsCaptor = ArgumentCaptor.forClass(TreeMap.class);
        service.createFlowByFiles("c-seal", "测试合同",
                "[{\"userName\":\"张三\",\"phone\":\"13800138000\"}]",
                new byte[]{1, 2, 3}, "c-seal.pdf");
        verify(apiClient).invoke(eq("CreateFlowByFiles"), paramsCaptor.capture());

        java.util.List<?> approvers = (java.util.List<?>) paramsCaptor.getValue().get("Approvers");
        assertEquals(2, approvers.size(), "companySeal=true 应追加乙方企业签署方");
        TreeMap<String, Object> company = (TreeMap<String, Object>) approvers.get(1);
        assertEquals(3, company.get("ApproverType"), "本企业自动盖章应为静默签署(3)");
        assertFalse(company.containsKey("RecipientId"),
                "乙方企业签署方不应带 RecipientId（CreateFlowByFiles 会报 UnknownParameter）");
        assertFalse(company.containsKey("ApproverMobile"),
                "ApproverType=3 签署人默认经办人，不应要求/携带 ApproverMobile");
        java.util.List<?> comps = (java.util.List<?>) company.get("SignComponents");
        TreeMap<String, Object> seal = (TreeMap<String, Object>) comps.get(0);
        assertEquals("SIGN_SEAL", seal.get("ComponentType"), "乙方应为签章控件");
        assertEquals("公章", seal.get("ComponentId"), "签章靠「公章」关键字定位");
        assertEquals("seal-id-abc123", seal.get("ComponentValue"), "自动签必须指定已授权印章ID");
    }

    @Test
    void createFlowByFiles_companySealEnabledButNoSealId_failsFastBeforeUpload() {
        // companySeal=true 但印章ID缺失（companySealId=null）
        service.setFileProperties(new EssFileProperties(
                true, "file.test.ess.tencent.cn", "电子签字", "", true,
                "公章", "天津源创智能科技有限公司", "Right", 120.0, 44.0, 5.0, 0.0,
                "Below", 100.0, 100.0, 0.0, 5.0, null));

        assertThrows(RuntimeException.class, () -> service.createFlowByFiles("c-noseal", "合同",
                "[{\"userName\":\"张三\",\"phone\":\"13800138000\"}]", new byte[]{1}, "x.pdf", true));
        // 上传前就拦下：UploadFiles 不应被调用
        verify(apiClient, never()).invoke(eq("UploadFiles"), any(), anyString());
    }

    @Test
    void createFlowByFiles_withoutFilePropertiesConfigured_shouldThrow() {
        // 未注入 EssFileProperties → 文件模式不可用
        assertThrows(IllegalStateException.class, () ->
                service.createFlowByFiles("c-f2", "x", "[{}]", new byte[]{1}, "x.pdf"));
    }

    @Test
    void createFlow_duplicateContract_shouldThrow() {
        when(flowRecordRepository.findByContractId("c-001"))
                .thenReturn(Optional.of(EssFlowRecord.create("c-001", "[{}]")));

        assertThrows(EssFlowException.class,
                () -> service.createFlow("c-001", "重复", "[{}]"));
    }

    @Test
    void startFlow_shouldUpdateStatus() {
        // Arrange
        EssFlowRecord record = EssFlowRecord.create("c-001", "[{}]");
        record.assignFlowId("flow-001");
        when(flowRecordRepository.findByContractId("c-001")).thenReturn(Optional.of(record));
        when(flowRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ObjectNode apiResponse = objectMapper.createObjectNode();
        when(apiClient.invoke(eq("StartFlow"), any())).thenReturn(apiResponse);

        // Act
        service.startFlow("c-001");

        // Assert
        assertEquals(FlowStatus.SIGNING, record.getFlowStatus());
        verify(apiClient).invoke(eq("StartFlow"), any());
    }

    @Test
    void startFlow_wrongStatus_shouldThrow() {
        EssFlowRecord record = EssFlowRecord.create("c-001", "[{}]");
        // status is INIT, not CREATED
        when(flowRecordRepository.findByContractId("c-001")).thenReturn(Optional.of(record));

        assertThrows(EssFlowException.class, () -> service.startFlow("c-001"));
    }

    @Test
    void describeFlowStatus_parsesRealEssResponseShape_FlowDetailInfos() {
        // 真实线上响应：FlowDetailInfos[0].FlowStatus = 4 (已签署完成)
        EssFlowRecord record = EssFlowRecord.create("c-001", "[{}]");
        record.assignFlowId("flow-001");
        record.startSigning();
        when(flowRecordRepository.findByContractId("c-001")).thenReturn(Optional.of(record));
        when(flowRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ObjectNode apiResponse = objectMapper.createObjectNode();
        com.fasterxml.jackson.databind.node.ArrayNode arr = objectMapper.createArrayNode();
        ObjectNode detail = objectMapper.createObjectNode();
        detail.put("FlowId", "flow-001");
        detail.put("FlowStatus", 4); // 腾讯 4 = 已签署完成
        arr.add(detail);
        apiResponse.set("FlowDetailInfos", arr);
        when(apiClient.invoke(eq("DescribeFlowInfo"), any())).thenReturn(apiResponse);

        FlowStatus status = service.describeFlowStatus("c-001");

        assertEquals(FlowStatus.COMPLETED, status,
                "FlowDetailInfos[0].FlowStatus=4 必须映射为内部 COMPLETED（修复响应解析 + 状态码映射）");
    }

    @Test
    void describeFlowStatus_legacyFlowInfoShape_stillWorks() {
        // 老的 mock 响应结构仍兼容（fallback 路径）
        EssFlowRecord record = EssFlowRecord.create("c-002", "[{}]");
        record.assignFlowId("flow-002");
        record.startSigning();
        when(flowRecordRepository.findByContractId("c-002")).thenReturn(Optional.of(record));
        when(flowRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ObjectNode apiResponse = objectMapper.createObjectNode();
        ObjectNode flowInfo = objectMapper.createObjectNode();
        flowInfo.put("Status", "4"); // 已签署完成，老 mock 结构
        apiResponse.set("FlowInfo", flowInfo);
        when(apiClient.invoke(eq("DescribeFlowInfo"), any())).thenReturn(apiResponse);

        FlowStatus status = service.describeFlowStatus("c-002");
        assertEquals(FlowStatus.COMPLETED, status);
    }

    @Test
    void describeFlowStatus_signingStatus_doesNotPromote() {
        // FlowStatus=1（待签署/部分签署）应保持 SIGNING，不能误判 COMPLETED
        EssFlowRecord record = EssFlowRecord.create("c-003", "[{}]");
        record.assignFlowId("flow-003");
        record.startSigning();
        when(flowRecordRepository.findByContractId("c-003")).thenReturn(Optional.of(record));
        lenient().when(flowRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ObjectNode apiResponse = objectMapper.createObjectNode();
        com.fasterxml.jackson.databind.node.ArrayNode arr = objectMapper.createArrayNode();
        ObjectNode detail = objectMapper.createObjectNode();
        detail.put("FlowStatus", 1);
        arr.add(detail);
        apiResponse.set("FlowDetailInfos", arr);
        when(apiClient.invoke(eq("DescribeFlowInfo"), any())).thenReturn(apiResponse);

        FlowStatus status = service.describeFlowStatus("c-003");
        assertEquals(FlowStatus.SIGNING, status);
    }

    @Test
    void describeFlowStatus_unknownStatus_keepsLocal() {
        // 线上观察到 FlowStatus=7（流程内部状态），必须保留本地 SIGNING，避免错误推进
        EssFlowRecord record = EssFlowRecord.create("c-004", "[{}]");
        record.assignFlowId("flow-004");
        record.startSigning();
        when(flowRecordRepository.findByContractId("c-004")).thenReturn(Optional.of(record));
        lenient().when(flowRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ObjectNode apiResponse = objectMapper.createObjectNode();
        com.fasterxml.jackson.databind.node.ArrayNode arr = objectMapper.createArrayNode();
        ObjectNode detail = objectMapper.createObjectNode();
        detail.put("FlowStatus", 7);
        arr.add(detail);
        apiResponse.set("FlowDetailInfos", arr);
        when(apiClient.invoke(eq("DescribeFlowInfo"), any())).thenReturn(apiResponse);

        FlowStatus status = service.describeFlowStatus("c-004");
        assertEquals(FlowStatus.SIGNING, status, "FlowStatus=7 不应被误映射为 COMPLETED");
    }
}
