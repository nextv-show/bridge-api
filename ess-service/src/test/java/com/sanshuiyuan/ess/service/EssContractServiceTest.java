package com.sanshuiyuan.ess.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

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
                "tpl-001", "https://cb.example.com", null, null, 5000, 10000, 3, Boolean.FALSE);
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
        // 真实线上响应：FlowDetailInfos[0].FlowStatus = 2 (已签署)
        EssFlowRecord record = EssFlowRecord.create("c-001", "[{}]");
        record.assignFlowId("flow-001");
        record.startSigning();
        when(flowRecordRepository.findByContractId("c-001")).thenReturn(Optional.of(record));
        when(flowRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ObjectNode apiResponse = objectMapper.createObjectNode();
        com.fasterxml.jackson.databind.node.ArrayNode arr = objectMapper.createArrayNode();
        ObjectNode detail = objectMapper.createObjectNode();
        detail.put("FlowId", "flow-001");
        detail.put("FlowStatus", 2); // 腾讯 2 = 已签署完成
        arr.add(detail);
        apiResponse.set("FlowDetailInfos", arr);
        when(apiClient.invoke(eq("DescribeFlowInfo"), any())).thenReturn(apiResponse);

        FlowStatus status = service.describeFlowStatus("c-001");

        assertEquals(FlowStatus.COMPLETED, status,
                "FlowDetailInfos[0].FlowStatus=2 必须映射为内部 COMPLETED（修复响应解析 + 状态码映射）");
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
        flowInfo.put("Status", "2"); // 已签署，老 mock 结构
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
