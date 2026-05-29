package com.sanshuiyuan.ess.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanshuiyuan.ess.config.EssProperties;
import com.sanshuiyuan.ess.domain.EssFlowRecord;
import com.sanshuiyuan.ess.infra.client.EssApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EssDocumentServiceTest {

    @Mock private EssApiClient apiClient;
    @Mock private EssContractService contractService;
    private ObjectMapper objectMapper;
    private EssDocumentService service;

    @BeforeEach
    void setUp() {
        EssProperties props = new EssProperties("sid", "skey", "op-001", "corp-001",
                "tpl-001", "https://cb.example.com", null, null, 5000, 10000, 3, Boolean.FALSE);
        objectMapper = new ObjectMapper();
        service = new EssDocumentService(apiClient, props, contractService, objectMapper);
    }

    private EssFlowRecord createMockRecord() {
        EssFlowRecord record = EssFlowRecord.create("c-001", "[{}]");
        record.assignFlowId("flow-001");
        return record;
    }

    @Test
    void getFileUrls_shouldReturnUrls() {
        when(contractService.findByContractId("c-001")).thenReturn(createMockRecord());

        ObjectNode response = objectMapper.createObjectNode();
        ArrayNode urls = objectMapper.createArrayNode();
        ObjectNode url1 = objectMapper.createObjectNode();
        url1.put("Url", "https://files.ess.tencent.com/doc1.pdf");
        urls.add(url1);
        ObjectNode url2 = objectMapper.createObjectNode();
        url2.put("Url", "https://files.ess.tencent.com/doc2.pdf");
        urls.add(url2);
        response.set("FileUrls", urls);
        when(apiClient.invoke(eq("DescribeFileUrls"), any())).thenReturn(response);

        List<String> result = service.getFileUrls("c-001");

        assertEquals(2, result.size());
        assertTrue(result.get(0).contains("doc1.pdf"));
        assertTrue(result.get(1).contains("doc2.pdf"));
    }

    @Test
    void getFileUrl_single_shouldReturnUrl() {
        when(contractService.findByContractId("c-001")).thenReturn(createMockRecord());

        ObjectNode response = objectMapper.createObjectNode();
        response.put("Url", "https://files.ess.tencent.com/single.pdf");
        when(apiClient.invoke(eq("DescribeFileUrls"), any())).thenReturn(response);

        String url = service.getFileUrl("c-001", "file-001");

        assertEquals("https://files.ess.tencent.com/single.pdf", url);
    }
}
