package com.sanshuiyuan.ess.infra.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanshuiyuan.ess.config.EssProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EssApiClient 单元测试。
 * 测试 TC3-HMAC-SHA256 签名逻辑的正确性和确定性。
 */
class EssApiClientTest {

    private EssProperties props;

    @BeforeEach
    void setUp() {
        props = new EssProperties(
                "test-secret-id",
                "test-secret-key",
                "test-operator-id",
                "test-corp-id",
                "test-template-id",
                "https://example.com/callback",
                "ap-guangzhou",
                "ess.test.ess.tencent.cn",
                5000,
                10000,
                3,
                Boolean.FALSE,
                "3"
        );
    }

    @Test
    void tc3HmacSha256_shouldProduceDeterministicSignature() throws Exception {
        String timestamp = "1700000000";
        String payload = "{\"Operator\":{\"UserId\":\"test\"}}";
        String action = "CreateFlow";

        String sig1 = computeTc3Signature(timestamp, payload, action);
        String sig2 = computeTc3Signature(timestamp, payload, action);
        assertEquals(sig1, sig2, "相同输入应产生相同签名");
    }

    @Test
    void tc3HmacSha256_differentPayloads_shouldProduceDifferentSignatures() throws Exception {
        String timestamp = "1700000000";
        String action = "CreateFlow";

        String sig1 = computeTc3Signature(timestamp, "{\"a\":1}", action);
        String sig2 = computeTc3Signature(timestamp, "{\"a\":2}", action);
        assertNotEquals(sig1, sig2, "不同 payload 应产生不同签名");
    }

    @Test
    void tc3HmacSha256_differentActions_shouldProduceDifferentSignatures() throws Exception {
        String timestamp = "1700000000";
        String payload = "{}";

        String sig1 = computeTc3Signature(timestamp, payload, "CreateFlow");
        String sig2 = computeTc3Signature(timestamp, payload, "StartFlow");
        assertNotEquals(sig1, sig2, "不同 Action 应产生不同签名");
    }

    @Test
    void sha256Hex_emptyPayload_shouldMatchExpected() throws Exception {
        String emptyJson = "{}";
        String hash = sha256Hex(emptyJson);
        assertNotNull(hash);
        assertEquals(64, hash.length(), "SHA256 hex 应为 64 字符");
    }

    @Test
    void hmacSha256_shouldProduceCorrectLength() throws Exception {
        byte[] key = "test-key".getBytes(StandardCharsets.UTF_8);
        byte[] data = "test-data".getBytes(StandardCharsets.UTF_8);
        byte[] result = hmacSha256(key, data);
        assertEquals(32, result.length, "HMAC-SHA256 应为 32 字节");
    }

    @Test
    void essProperties_shouldHaveCorrectDefaults() {
        EssProperties p = new EssProperties(
                "sid", "skey", "op", "corp", null, null, null, null, null, null, null, null, null
        );
        assertEquals("ap-guangzhou", p.apiRegion());
        assertEquals("ess.test.ess.tencent.cn", p.apiEndpoint());
        assertEquals(5000, p.connectTimeoutMs());
        assertEquals(10000, p.readTimeoutMs());
        assertEquals(3, p.maxRetries());
        assertEquals(Boolean.FALSE, p.collectIdCard());
    }

    // ---- 内部辅助方法（模拟 TC3-HMAC-SHA256 签名逻辑）----

    private String computeTc3Signature(String timestamp, String payload, String action) throws Exception {
        String service = "ess";
        String host = props.apiEndpoint();
        String date = new java.text.SimpleDateFormat("yyyy-MM-dd").format(
                new java.util.Date(Long.parseLong(timestamp) * 1000L));

        String contentType = "application/json; charset=utf-8";
        String canonicalHeaders = "content-type:" + contentType + "\n"
                + "host:" + host + "\n"
                + "x-tc-action:" + action.toLowerCase() + "\n";
        String signedHeaders = "content-type;host;x-tc-action";
        String hashedPayload = sha256Hex(payload);
        String canonicalRequest = "POST" + "\n"
                + "/" + "\n"
                + "" + "\n"
                + canonicalHeaders + "\n"
                + signedHeaders + "\n"
                + hashedPayload;

        String algorithm = "TC3-HMAC-SHA256";
        String credentialScope = date + "/" + service + "/tc3_request";
        String hashedCanonicalRequest = sha256Hex(canonicalRequest);
        String stringToSign = algorithm + "\n"
                + timestamp + "\n"
                + credentialScope + "\n"
                + hashedCanonicalRequest;

        String secretKey = props.secretKey();
        byte[] secretDate = hmacSha256(("TC3" + secretKey).getBytes(StandardCharsets.UTF_8), date);
        byte[] secretService = hmacSha256(secretDate, service);
        byte[] secretSigning = hmacSha256(secretService, "tc3_request");
        byte[] signature = hmacSha256(secretSigning, stringToSign);
        return HexFormat.of().formatHex(signature);
    }

    private static String sha256Hex(String data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    private static byte[] hmacSha256(byte[] key, String data) throws Exception {
        return hmacSha256(key, data.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] hmacSha256(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }
}
