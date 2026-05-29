package com.sanshuiyuan.ess.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanshuiyuan.ess.config.EssProperties;
import com.sanshuiyuan.ess.domain.EssFlowRecord;
import com.sanshuiyuan.ess.exception.EssCallbackVerificationException;
import com.sanshuiyuan.ess.infra.repository.EssFlowRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EssCallbackServiceTest {

    @Mock private EssFlowRecordRepository flowRecordRepository;
    private EssProperties properties;
    private ObjectMapper objectMapper;
    private EssCallbackService service;

    @BeforeEach
    void setUp() {
        properties = new EssProperties("sid", "skey", "op-001", "corp-001",
                "tpl-001", "https://cb.example.com", null, null, 5000, 10000, 3, Boolean.FALSE);
        objectMapper = new ObjectMapper();
        service = new EssCallbackService(properties, flowRecordRepository, objectMapper);
    }

    private String computeSignature(String body, String timestamp) throws Exception {
        String data = timestamp + body;
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(
                properties.secretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Test
    void verifySignature_valid_shouldPass() throws Exception {
        String body = "{\"FlowId\":\"f-001\"}";
        String timestamp = "1700000000";
        String sig = computeSignature(body, timestamp);

        // Should not throw
        assertDoesNotThrow(() -> service.verifySignature(body, sig, timestamp));
    }

    @Test
    void verifySignature_invalid_shouldThrow() {
        String body = "{\"FlowId\":\"f-001\"}";
        String timestamp = "1700000000";

        assertThrows(EssCallbackVerificationException.class,
                () -> service.verifySignature(body, "invalid-sig", timestamp));
    }

    @Test
    void verifySignature_nullSignature_shouldThrow() {
        assertThrows(EssCallbackVerificationException.class,
                () -> service.verifySignature("{}", null, "1700000000"));
    }

    @Test
    void verifySignature_nullTimestamp_shouldThrow() {
        assertThrows(EssCallbackVerificationException.class,
                () -> service.verifySignature("{}", "somesig", null));
    }

    @Test
    void handleCallback_flowFinished_shouldComplete() throws Exception {
        String body = "{\"FlowId\":\"flow-001\",\"EventType\":\"FlowFinished\"}";
        String timestamp = "1700000000";
        String sig = computeSignature(body, timestamp);

        EssFlowRecord record = EssFlowRecord.create("c-001", "[{}]");
        record.assignFlowId("flow-001");
        when(flowRecordRepository.findByEssFlowId("flow-001"))
                .thenReturn(Optional.of(record));
        when(flowRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.handleCallback(body, sig, timestamp);

        assertTrue(result.success());
        assertEquals("c-001", result.contractId());
        assertEquals("FlowFinished", result.eventType());
    }

    @Test
    void handleCallback_noFlowId_shouldIgnore() throws Exception {
        String body = "{\"EventType\":\"Test\"}";
        String timestamp = "1700000000";
        String sig = computeSignature(body, timestamp);

        var result = service.handleCallback(body, sig, timestamp);
        assertFalse(result.success());
    }

    @Test
    void handleCallback_unknownFlow_shouldIgnore() throws Exception {
        String body = "{\"FlowId\":\"unknown-flow\",\"EventType\":\"Test\"}";
        String timestamp = "1700000000";
        String sig = computeSignature(body, timestamp);

        when(flowRecordRepository.findByEssFlowId("unknown-flow"))
                .thenReturn(Optional.empty());

        var result = service.handleCallback(body, sig, timestamp);
        assertFalse(result.success());
    }
}
