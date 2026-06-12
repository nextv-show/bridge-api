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
                "tpl-001", "https://cb.example.com", null, null, 5000, 10000, 3, Boolean.FALSE, "3");
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
    void generateH5SignUrl_newApiSchemeUrlField_shouldReturnUrl() {
        // 腾讯 ESS 2023+ 新版 CreateSchemeUrl 响应使用 SchemeUrl 字段（不是 H5SignUrl）。
        // 历史代码只读 H5SignUrl → 返回 null → 前端 if (signData.signUrl) 跳过 → 用户卡在等待。
        EssFlowRecord record = createMockRecord();
        when(contractService.findByContractId("c-001")).thenReturn(record);

        ObjectNode response = objectMapper.createObjectNode();
        response.put("SchemeUrl", "https://qian.tencent.cn/h5sign?token=abc");
        when(apiClient.invoke(eq("CreateSchemeUrl"), any())).thenReturn(response);

        String url = service.generateH5SignUrl("c-001", "signer-001",
                "https://h5.sanshuiyuan.com/checkout?contractId=1", "jump");

        assertEquals("https://qian.tencent.cn/h5sign?token=abc", url);
    }

    @Test
    void generateH5SignUrl_schemeUrlPrecedesLegacy_whenBothPresent() {
        // 新旧字段同时存在时优先取新字段 SchemeUrl
        EssFlowRecord record = createMockRecord();
        when(contractService.findByContractId("c-001")).thenReturn(record);

        ObjectNode response = objectMapper.createObjectNode();
        response.put("SchemeUrl", "https://new.ess/scheme");
        response.put("H5SignUrl", "https://legacy.ess/h5");
        when(apiClient.invoke(eq("CreateSchemeUrl"), any())).thenReturn(response);

        String url = service.generateH5SignUrl("c-001", "signer-001", null, "jump");
        assertEquals("https://new.ess/scheme", url);
    }

    @Test
    void generateH5SignUrl_allFieldsMissing_returnsNullButDoesNotThrow() {
        // 所有已知字段都缺失：返回 null（不抛），日志会 WARN 出实际字段供排障
        EssFlowRecord record = createMockRecord();
        when(contractService.findByContractId("c-001")).thenReturn(record);

        ObjectNode response = objectMapper.createObjectNode();
        response.put("RequestId", "abc");
        when(apiClient.invoke(eq("CreateSchemeUrl"), any())).thenReturn(response);

        String url = service.generateH5SignUrl("c-001", "signer-001", null, "jump");
        assertNull(url);
    }

    @Test
    void generateH5SignUrl_emptyStringField_treatedAsMissing() {
        // 字段存在但是空字符串：跳过，尝试后续字段
        EssFlowRecord record = createMockRecord();
        when(contractService.findByContractId("c-001")).thenReturn(record);

        ObjectNode response = objectMapper.createObjectNode();
        response.put("SchemeUrl", "");
        response.put("H5SignUrl", "https://legacy.ess/h5");
        when(apiClient.invoke(eq("CreateSchemeUrl"), any())).thenReturn(response);

        String url = service.generateH5SignUrl("c-001", "signer-001", null, "jump");
        assertEquals("https://legacy.ess/h5", url);
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
