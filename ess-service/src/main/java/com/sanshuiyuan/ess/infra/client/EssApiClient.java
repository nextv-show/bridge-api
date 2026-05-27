package com.sanshuiyuan.ess.infra.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanshuiyuan.ess.config.EssProperties;
import com.sanshuiyuan.ess.exception.EssApiException;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.TreeMap;

/**
 * 腾讯电子签企业版 HTTP 客户端封装。
 * <p>
 * 负责：HTTP 调用、TC3-HMAC-SHA256 签名、鉴权、错误处理、超时控制。
 * 所有 API 调用通过此客户端统一出口，确保审计日志可追踪。
 */
@Component
public class EssApiClient {

    private static final Logger log = LoggerFactory.getLogger(EssApiClient.class);
    private static final String SERVICE = "essbasic";
    private static final String HOST = "essbasic.tencentcloudapi.com";
    private static final String ALGORITHM = "TC3-HMAC-SHA256";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of("UTC"));

    private final EssProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public EssApiClient(EssProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restTemplate = createRestTemplate(properties);
    }

    /**
     * 调用腾讯电子签企业版 API。
     *
     * @param action API 动作名（如 CreateFlow, StartFlow）
     * @param params 请求参数
     * @return 响应 JSON
     * @throws EssApiException 调用失败时抛出
     */
    public JsonNode invoke(String action, TreeMap<String, Object> params) {
        long start = System.currentTimeMillis();
        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(params);
        } catch (Exception e) {
            throw new EssApiException(action, "序列化请求参数失败", e);
        }

        try {
            String timestamp = String.valueOf(Instant.now().getEpochSecond());
            HttpHeaders headers = buildAuthHeaders(action, requestBody, timestamp);

            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
            String url = "https://" + properties.apiEndpoint();

            String responseBody = restTemplate.postForObject(url, entity, String.class);
            long duration = System.currentTimeMillis() - start;

            JsonNode response = objectMapper.readTree(responseBody);
            JsonNode responseNode = response.get("Response");

            if (responseNode != null && responseNode.has("Error")) {
                String errorCode = responseNode.get("Error").get("Code").asText();
                String errorMsg = responseNode.get("Error").get("Message").asText();
                throw new EssApiException(action, 200,
                        String.format("API Error [%s]: %s", errorCode, errorMsg));
            }

            log.info("ESS API [{}] 调用成功, 耗时 {}ms", action, duration);
            return responseNode != null ? responseNode : response;

        } catch (EssApiException e) {
            throw e;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("ESS API [{}] 调用异常, 耗时 {}ms: {}", action, duration, e.getMessage());
            throw new EssApiException(action, e.getMessage(), e);
        }
    }

    /**
     * 构建 TC3-HMAC-SHA256 鉴权头。
     */
    HttpHeaders buildAuthHeaders(String action, String payload, String timestamp) {
        try {
            String date = DATE_FMT.format(Instant.ofEpochSecond(Long.parseLong(timestamp)));

            // 1. 拼接规范请求串
            String hashedPayload = sha256Hex(payload);
            String canonicalRequest = "POST\n/\n\ncontent-type:application/json; charset=utf-8\nhost:" + HOST + "\n\ncontent-type;host\n" + hashedPayload;

            // 2. 拼接待签名字符串
            String credentialScope = date + "/" + SERVICE + "/tc3_request";
            String hashedCanonicalRequest = sha256Hex(canonicalRequest);
            String stringToSign = ALGORITHM + "\n" + timestamp + "\n" + credentialScope + "\n" + hashedCanonicalRequest;

            // 3. 计算签名
            byte[] secretDate = hmacSha256(("TC3" + properties.secretKey()).getBytes(StandardCharsets.UTF_8), date);
            byte[] secretService = hmacSha256(secretDate, SERVICE);
            byte[] secretSigning = hmacSha256(secretService, "tc3_request");
            String signature = hexEncode(hmacSha256(secretSigning, stringToSign));

            // 4. 构建 Authorization
            String authorization = ALGORITHM + " Credential=" + properties.secretId() + "/" + credentialScope
                    + ", SignedHeaders=content-type;host, Signature=" + signature;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Host", HOST);
            headers.set("X-TC-Action", action);
            headers.set("X-TC-Version", "2021-05-26");
            headers.set("X-TC-Timestamp", timestamp);
            headers.set("X-TC-Region", properties.apiRegion());
            headers.set("Authorization", authorization);

            return headers;
        } catch (Exception e) {
            throw new EssApiException(action, "构建鉴权头失败", e);
        }
    }

    private RestTemplate createRestTemplate(EssProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.connectTimeoutMs());
        factory.setReadTimeout(props.readTimeoutMs());
        return new RestTemplate(factory);
    }

    private static String sha256Hex(String data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
        return hexEncode(hash);
    }

    private static byte[] hmacSha256(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private static String hexEncode(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
