package com.sanshuiyuan.ess.infra.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanshuiyuan.ess.config.EssProperties;
import com.sanshuiyuan.ess.exception.EssApiException;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * 腾讯电子签 API 客户端。
 * <p>
 * 使用 TC3-HMAC-SHA256 手动签名 + OkHttp 发送。
 * 参考腾讯官方 ess-java-kit 的签名逻辑，但不依赖 SDK 的 call() 方法
 * （SDK call() 在联调环境存在 Endpoint/Version 兼容问题）。
 */
@Component
public class EssApiClient {

    private static final Logger log = LoggerFactory.getLogger(EssApiClient.class);

    private final EssProperties properties;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;

    public EssApiClient(EssProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @Retryable(
            retryFor = {EssApiException.class},
            noRetryFor = {
                    com.sanshuiyuan.ess.exception.EssFlowException.class,
                    com.sanshuiyuan.ess.exception.EssCallbackVerificationException.class
            },
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000)
    )
    public JsonNode invoke(String action, TreeMap<String, Object> params) {
        long start = System.currentTimeMillis();
        try {
            String body = objectMapper.writeValueAsString(params);
            String endpoint = properties.apiEndpoint();
            String service = "ess";

            log.info("ESS API [action={}, bodyLen={}, endpoint={}]", action, body.length(), properties.apiEndpoint());

            // TC3-HMAC-SHA256 签名
            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            String timestamp = String.valueOf(Instant.now().getEpochSecond());
            String date = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                    .withZone(ZoneId.of("UTC")).format(Instant.now());

            String hashedPayload = sha256Hex(bodyBytes);
            String canonicalRequest = "POST\n/\n\ncontent-type:application/json; charset=utf-8\nhost:"
                    + endpoint + "\n\ncontent-type;host\n" + hashedPayload;
            String credentialScope = date + "/" + service + "/tc3_request";
            String stringToSign = "TC3-HMAC-SHA256\n" + timestamp + "\n" + credentialScope
                    + "\n" + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));

            byte[] secretDate = hmacSha256(("TC3" + properties.secretKey()).getBytes(StandardCharsets.UTF_8), date);
            byte[] secretService = hmacSha256(secretDate, service);
            byte[] secretSigning = hmacSha256(secretService, "tc3_request");
            String signature = hexEncode(hmacSha256(secretSigning, stringToSign));

            String authorization = "TC3-HMAC-SHA256 Credential=" + properties.secretId() + "/" + credentialScope
                    + ", SignedHeaders=content-type;host, Signature=" + signature;

            // OkHttp 发送
            Request request = new Request.Builder()
                    .url("https://" + endpoint + "/")
                    .addHeader("Content-Type", "application/json; charset=utf-8")
                    .addHeader("Host", endpoint)
                    .addHeader("X-TC-Action", action)
                    .addHeader("X-TC-Version", "2020-11-11")
                    .addHeader("X-TC-Timestamp", timestamp)
                    .addHeader("X-TC-Region", properties.apiRegion())
                    .addHeader("Authorization", authorization)
                    .post(RequestBody.create(bodyBytes, MediaType.parse("application/json; charset=utf-8")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                long duration = System.currentTimeMillis() - start;
                String responseBody = response.body() != null ? response.body().string() : "";

                log.info("ESS API [action={}] 耗时 {}ms, httpCode={}", action, duration, response.code());

                JsonNode responseJson = objectMapper.readTree(responseBody);
                JsonNode responseNode = responseJson.get("Response");

                if (responseNode != null && responseNode.has("Error")) {
                    String errorCode = responseNode.get("Error").get("Code").asText();
                    String errorMsg = responseNode.get("Error").get("Message").asText();
                    log.error("ESS API [action={}] 业务错误 [{}]: {}", action, errorCode, errorMsg);
                    throw new EssApiException(action, response.code(),
                            String.format("API Error [%s]: %s", errorCode, errorMsg));
                }

                return responseNode != null ? responseNode : responseJson;
            }

        } catch (EssApiException e) {
            throw e;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("ESS API [action={}] 调用异常, 耗时 {}ms: {}", action, duration, e.getMessage());
            throw new EssApiException(action, e.getMessage(), e);
        }
    }

    private static String sha256Hex(byte[] data) throws Exception {
        return hexEncode(MessageDigest.getInstance("SHA-256").digest(data));
    }

    private static byte[] hmacSha256(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private static String hexEncode(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
