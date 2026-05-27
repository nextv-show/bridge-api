package com.sanshuiyuan.ess.infra.client;

import com.sanshuiyuan.ess.config.EssProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class EssApiClientTest {

    private EssApiClient client;

    @BeforeEach
    void setUp() {
        EssProperties props = new EssProperties(
                "test-secret-id",
                "test-secret-key",
                "test-operator-id",
                "test-corp-id",
                "test-template-id",
                "https://example.com/callback",
                "ap-guangzhou",
                "essbasic.tencentcloudapi.com",
                5000,
                10000,
                3
        );
        client = new EssApiClient(props, new com.fasterxml.jackson.databind.ObjectMapper());
    }

    @Test
    void buildAuthHeaders_shouldContainRequiredHeaders() {
        // Given
        TreeMap<String, Object> params = new TreeMap<>();
        params.put("TestKey", "TestValue");

        // When
        var headers = client.buildAuthHeaders("CreateFlow", "{}", "1700000000");

        // Then
        assertNotNull(headers);
        assertEquals("CreateFlow", headers.getFirst("X-TC-Action"));
        assertEquals("2021-05-26", headers.getFirst("X-TC-Version"));
        assertEquals("1700000000", headers.getFirst("X-TC-Timestamp"));
        assertEquals("ap-guangzhou", headers.getFirst("X-TC-Region"));
        assertNotNull(headers.getFirst("Authorization"));
        assertTrue(headers.getFirst("Authorization").startsWith("TC3-HMAC-SHA256"));
        assertTrue(headers.getFirst("Authorization").contains("test-secret-id"));
    }

    @Test
    void buildAuthHeaders_shouldProduceDeterministicSignature() {
        // Same inputs should produce same signature
        var headers1 = client.buildAuthHeaders("StartFlow", "{}", "1700000000");
        var headers2 = client.buildAuthHeaders("StartFlow", "{}", "1700000000");

        assertEquals(headers1.getFirst("Authorization"), headers2.getFirst("Authorization"));
    }

    @Test
    void buildAuthHeaders_differentActions_shouldHaveDifferentActionHeaders() {
        var headers1 = client.buildAuthHeaders("CreateFlow", "{}", "1700000000");
        var headers2 = client.buildAuthHeaders("StartFlow", "{}", "1700000000");

        assertNotEquals(headers1.getFirst("X-TC-Action"), headers2.getFirst("X-TC-Action"));
        assertEquals("CreateFlow", headers1.getFirst("X-TC-Action"));
        assertEquals("StartFlow", headers2.getFirst("X-TC-Action"));
    }

    @Test
    void buildAuthHeaders_differentPayloads_shouldProduceDifferentSignatures() {
        var headers1 = client.buildAuthHeaders("CreateFlow", "{\"a\":1}", "1700000000");
        var headers2 = client.buildAuthHeaders("CreateFlow", "{\"a\":2}", "1700000000");

        assertNotEquals(headers1.getFirst("Authorization"), headers2.getFirst("Authorization"));
    }
}
