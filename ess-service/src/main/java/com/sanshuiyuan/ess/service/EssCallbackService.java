package com.sanshuiyuan.ess.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanshuiyuan.ess.config.EssProperties;
import com.sanshuiyuan.ess.domain.EssFlowRecord;
import com.sanshuiyuan.ess.domain.FlowStatus;
import com.sanshuiyuan.ess.exception.EssCallbackVerificationException;
import com.sanshuiyuan.ess.exception.EssFlowException;
import com.sanshuiyuan.ess.infra.repository.EssFlowRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * ESS 回调服务。
 * <p>
 * 负责接收 Webhook 回调、验证签名、分发事件到流程记录。
 */
@Service
public class EssCallbackService {

    private static final Logger log = LoggerFactory.getLogger(EssCallbackService.class);

    private final EssProperties properties;
    private final EssFlowRecordRepository flowRecordRepository;
    private final ObjectMapper objectMapper;

    public EssCallbackService(EssProperties properties,
                               EssFlowRecordRepository flowRecordRepository,
                               ObjectMapper objectMapper) {
        this.properties = properties;
        this.flowRecordRepository = flowRecordRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 处理 ESS Webhook 回调。
     *
     * @param requestBody   原始请求体
     * @param signature     回调签名
     * @param timestamp     回调时间戳
     * @return 处理结果
     */
    @Transactional
    public CallbackResult handleCallback(String requestBody, String signature, String timestamp) {
        log.info("收到 ESS 回调, timestamp={}", timestamp);

        // 1. 验证签名
        verifySignature(requestBody, signature, timestamp);

        try {
            // 2. 解析回调内容
            JsonNode callbackData = objectMapper.readTree(requestBody);
            String flowId = extractFlowId(callbackData);
            String eventType = extractEventType(callbackData);

            if (flowId == null || flowId.isEmpty()) {
                log.warn("回调数据缺少 FlowId，忽略");
                return CallbackResult.ignored("缺少 FlowId");
            }

            // 3. 查找流程记录
            EssFlowRecord record = flowRecordRepository.findByEssFlowId(flowId)
                    .orElse(null);

            if (record == null) {
                log.warn("未找到流程记录 [flowId={}]，忽略回调", flowId);
                return CallbackResult.ignored("流程不存在: " + flowId);
            }

            // 4. 根据事件类型更新状态
            updateFlowStatus(record, eventType, callbackData);
            flowRecordRepository.save(record);

            log.info("ESS 回调处理成功 [contractId={}, flowId={}, event={}]",
                    record.getContractId(), flowId, eventType);

            return CallbackResult.success(record.getContractId(), flowId, eventType);

        } catch (Exception e) {
            log.error("处理 ESS 回调异常: {}", e.getMessage(), e);
            throw new EssFlowException("unknown", "处理回调失败: " + e.getMessage());
        }
    }

    /**
     * 验证回调签名。
     * <p>
     * 使用 HMAC-SHA256(secretKey, timestamp + requestBody) 校验。
     */
    void verifySignature(String requestBody, String signature, String timestamp) {
        if (signature == null || signature.isEmpty()) {
            throw new EssCallbackVerificationException("签名参数为空");
        }
        if (timestamp == null || timestamp.isEmpty()) {
            throw new EssCallbackVerificationException("时间戳参数为空");
        }

        try {
            String data = timestamp + requestBody;
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                    properties.secretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            String expected = hexEncode(hash);

            if (!expected.equalsIgnoreCase(signature)) {
                log.warn("回调签名验证失败 [expected={}, actual={}]", expected, signature);
                throw new EssCallbackVerificationException(
                        String.format("签名不匹配 (expected=%s...)", expected.substring(0, 8)));
            }

            log.debug("回调签名验证通过");
        } catch (EssCallbackVerificationException e) {
            throw e;
        } catch (Exception e) {
            throw new EssCallbackVerificationException("签名验证失败", e);
        }
    }

    private String extractFlowId(JsonNode data) {
        if (data.has("FlowId")) return data.get("FlowId").asText();
        if (data.has("FlowId")) return data.get("FlowId").asText();
        if (data.has("Data") && data.get("Data").has("FlowId")) {
            return data.get("Data").get("FlowId").asText();
        }
        return null;
    }

    private String extractEventType(JsonNode data) {
        if (data.has("EventType")) return data.get("EventType").asText();
        if (data.has("Event")) return data.get("Event").asText();
        if (data.has("Action")) return data.get("Action").asText();
        return "UNKNOWN";
    }

    private void updateFlowStatus(EssFlowRecord record, String eventType, JsonNode callbackData) {
        String dataJson = callbackData.toString();

        switch (eventType) {
            case "FlowStatusChanged", "SignComplete" -> {
                String status = callbackData.has("FlowStatus")
                        ? callbackData.get("FlowStatus").asText() : null;
                FlowStatus mapped = mapStatus(status);
                if (mapped != null) {
                    applyStatus(record, mapped, dataJson);
                }
            }
            case "FlowFinished" -> record.complete(dataJson);
            case "FlowCancelled" -> record.cancel();
            case "FlowRejected" -> record.reject(dataJson);
            default -> record.updateCallbackData(dataJson);
        }
    }

    private FlowStatus mapStatus(String status) {
        if (status == null) return null;
        return switch (status) {
            case "0", "INIT" -> FlowStatus.INIT;
            case "1", "CREATED" -> FlowStatus.CREATED;
            case "2", "SIGNING" -> FlowStatus.SIGNING;
            case "3", "COMPLETED", "FINISH" -> FlowStatus.COMPLETED;
            case "4", "CANCELLED" -> FlowStatus.CANCELLED;
            case "5", "REJECTED" -> FlowStatus.REJECTED;
            case "6", "EXPIRED" -> FlowStatus.EXPIRED;
            default -> null;
        };
    }

    private void applyStatus(EssFlowRecord record, FlowStatus status, String data) {
        switch (status) {
            case COMPLETED -> record.complete(data);
            case CANCELLED -> record.cancel();
            case REJECTED -> record.reject(data);
            case ERROR -> record.markError(data);
            default -> record.updateCallbackData(data);
        }
    }

    private static String hexEncode(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 回调处理结果。
     */
    public record CallbackResult(boolean success, String contractId, String flowId,
                                  String eventType, String message) {
        public static CallbackResult success(String contractId, String flowId, String eventType) {
            return new CallbackResult(true, contractId, flowId, eventType, "OK");
        }
        public static CallbackResult ignored(String message) {
            return new CallbackResult(false, null, null, null, message);
        }
    }
}
