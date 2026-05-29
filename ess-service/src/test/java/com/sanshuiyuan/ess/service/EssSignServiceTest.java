package com.sanshuiyuan.ess.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanshuiyuan.ess.config.EssProperties;
import com.sanshuiyuan.ess.domain.EssFlowRecord;
import com.sanshuiyuan.ess.exception.EssFlowException;
import com.sanshuiyuan.ess.infra.client.EssApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EssSignServiceTest {

    @Mock private EssApiClient apiClient;
    @Mock private EssContractService contractService;
    private EssProperties properties;
    private ObjectMapper objectMapper;
    private EssSignService service;

    @BeforeEach
    void setUp() {
        properties = new EssProperties("sid", "skey", "op-001", "corp-001",
                "tpl-001", "https://cb.example.com", null, null, 5000, 10000, 3, Boolean.FALSE);
        objectMapper = new ObjectMapper();
        service = new EssSignService(apiClient, properties, contractService, objectMapper);
    }

    private EssFlowRecord createMockRecord() {
        EssFlowRecord record = EssFlowRecord.create("c-001", "[{}]");
        record.assignFlowId("flow-001");
        return record;
    }

    @Test
    void generateH5SignUrl_jumpMode_shouldReturnUrl() {
        EssFlowRecord record = createMockRecord();
        when(contractService.findByContractId("c-001")).thenReturn(record);

        ObjectNode response = objectMapper.createObjectNode();
        response.put("H5SignUrl", "https://sign.ess.tencent.com/h5?token=***");
        when(apiClient.invoke(eq("CreateSchemeUrl"), any())).thenReturn(response);

        String url = service.generateH5SignUrl("c-001", "signer-001",
                "https://app.example.com/done", "jump");

        assertEquals("https://sign.ess.tencent.com/h5?token=***", url);
        verify(apiClient).invoke(eq("CreateSchemeUrl"), any());
    }

    @Test
    void generateH5SignUrl_embedMode_shouldWork() {
        EssFlowRecord record = createMockRecord();
        when(contractService.findByContractId("c-001")).thenReturn(record);

        ObjectNode response = objectMapper.createObjectNode();
        response.put("H5SignUrl", "https://embed.ess.tencent.com?token=***");
        when(apiClient.invoke(eq("CreateSchemeUrl"), any())).thenReturn(response);

        String url = service.generateH5SignUrl("c-001", "signer-001", null, "embed");
        assertNotNull(url);
    }

    @Test
    void generateMiniAppSignParams_shouldReturnJson() {
        EssFlowRecord record = createMockRecord();
        when(contractService.findByContractId("c-001")).thenReturn(record);

        ObjectNode response = objectMapper.createObjectNode();
        response.put("SignParams", "encrypted-data");
        when(apiClient.invoke(eq("CreateSchemeUrl"), any())).thenReturn(response);

        JsonNode result = service.generateMiniAppSignParams("c-001", "signer-001", "wx1234");
        assertNotNull(result);
    }

    @Test
    void generateAppSignParams_shouldReturnJson() {
        EssFlowRecord record = createMockRecord();
        when(contractService.findByContractId("c-001")).thenReturn(record);

        ObjectNode response = objectMapper.createObjectNode();
        response.put("AppSignParams", "app-encrypted-data");
        when(apiClient.invoke(eq("CreateSchemeUrl"), any())).thenReturn(response);

        JsonNode result = service.generateAppSignParams("c-001", "signer-001", "android");
        assertNotNull(result);
    }

    @Test
    void createServerSign_shouldInvokeApi() {
        EssFlowRecord record = createMockRecord();
        when(contractService.findByContractId("c-001")).thenReturn(record);

        ObjectNode response = objectMapper.createObjectNode();
        when(apiClient.invoke(eq("CreateServerSign"), any())).thenReturn(response);

        service.createServerSign("c-001");

        verify(apiClient).invoke(eq("CreateServerSign"), any());
    }
}
