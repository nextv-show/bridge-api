package com.sanshuiyuan.ess.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanshuiyuan.ess.config.EssProperties;
import com.sanshuiyuan.ess.domain.EssFlowRecord;
import com.sanshuiyuan.ess.infra.repository.EssFlowRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EssCallbackServiceTest {

    @Mock private EssFlowRecordRepository flowRecordRepository;
    @Mock private EssContractService essContractService;
    private EssProperties properties;
    private ObjectMapper objectMapper;
    private EssCallbackService service;

    @BeforeEach
    void setUp() {
        properties = new EssProperties("sid", "skey", "op-001", "corp-001",
                "tpl-001", "https://cb.example.com", null, null, 5000, 10000, 3, Boolean.FALSE, "3");
        objectMapper = new ObjectMapper();
        service = new EssCallbackService(properties, flowRecordRepository, objectMapper);
        service.setEssContractService(essContractService);
    }

    @Test
    void handleCallback_triggersAuthoritativeQuery_notTrustingBody() {
        // 回调不再信任 body 状态，仅以 FlowId 触发服务端权威查单。
        String body = "{\"FlowId\":\"flow-001\",\"EventType\":\"FlowFinished\"}";
        EssFlowRecord record = EssFlowRecord.create("c-001", "[{}]");
        record.assignFlowId("flow-001");
        when(flowRecordRepository.findByEssFlowId("flow-001")).thenReturn(Optional.of(record));

        var result = service.handleCallback(body, null, null); // 签名/时间戳参数已无意义

        assertTrue(result.success());
        assertEquals("c-001", result.contractId());
        assertEquals("FlowFinished", result.eventType());
        // 关键：以 describeFlowStatus（TC3 签名）为权威，而非按回调体改状态。
        verify(essContractService).describeFlowStatus("c-001");
    }

    @Test
    void handleCallback_noFlowId_shouldIgnore() {
        var result = service.handleCallback("{\"EventType\":\"Test\"}", null, null);
        assertFalse(result.success());
        verify(essContractService, never()).describeFlowStatus(any());
    }

    @Test
    void handleCallback_unknownFlow_shouldIgnore() {
        when(flowRecordRepository.findByEssFlowId("unknown-flow")).thenReturn(Optional.empty());

        var result = service.handleCallback("{\"FlowId\":\"unknown-flow\"}", null, null);
        assertFalse(result.success());
        verify(essContractService, never()).describeFlowStatus(any());
    }
}
