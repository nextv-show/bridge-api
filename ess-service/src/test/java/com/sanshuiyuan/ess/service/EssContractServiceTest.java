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
                "tpl-001", "https://cb.example.com", null, null, 5000, 10000, 3);
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
    void describeFlowStatus_shouldReturnAndUpdateStatus() {
        EssFlowRecord record = EssFlowRecord.create("c-001", "[{}]");
        record.assignFlowId("flow-001");
        record.startSigning();
        when(flowRecordRepository.findByContractId("c-001")).thenReturn(Optional.of(record));
        when(flowRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ObjectNode apiResponse = objectMapper.createObjectNode();
        apiResponse.put("FlowStatus", "3"); // COMPLETED
        when(apiClient.invoke(eq("DescribeFlowStatus"), any())).thenReturn(apiResponse);

        FlowStatus status = service.describeFlowStatus("c-001");

        assertEquals(FlowStatus.COMPLETED, status);
    }
}
