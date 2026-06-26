package com.sanshuiyuan.ess.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanshuiyuan.ess.config.EssProperties;
import com.sanshuiyuan.ess.domain.EssFlowRecord;
import com.sanshuiyuan.ess.domain.FlowStatus;
import com.sanshuiyuan.ess.exception.EssFlowException;
import com.sanshuiyuan.ess.infra.repository.EssFlowRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    /**
     * Contract 状态机桥接（可选注入；单测不依赖）。setter 注入避开 EssContractService → bridge 循环。
     */
    private ContractCompletionBridge completionBridge;

    public EssCallbackService(EssProperties properties,
                               EssFlowRecordRepository flowRecordRepository,
                               ObjectMapper objectMapper) {
        this.properties = properties;
        this.flowRecordRepository = flowRecordRepository;
        this.objectMapper = objectMapper;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setCompletionBridge(ContractCompletionBridge completionBridge) {
        this.completionBridge = completionBridge;
    }

    /**
     * 权威查单（setter 注入，避开构造期循环）。回调只作触发器，状态以服务端 describeFlowStatus 为准。
     */
    private EssContractService essContractService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setEssContractService(EssContractService essContractService) {
        this.essContractService = essContractService;
    }

    /**
     * 按 FlowId 的查单节流：回调端点是公开 permitAll，去掉验签后需防止「拿已知 FlowId 反复触发
     * 对腾讯的 DescribeFlowInfo」刷配额/占线程。同一 FlowId 在窗口内只放行一次权威查单——同时天然
     * 合并腾讯自身对同一事件的重试。窗口外（状态可能又变）放行下一次。
     */
    private static final long QUERY_THROTTLE_MS = 30_000L;
    private final java.util.concurrent.ConcurrentHashMap<String, Long> lastQueryAtMs =
            new java.util.concurrent.ConcurrentHashMap<>();

    private boolean queryThrottled(String flowId) {
        long now = System.currentTimeMillis();
        if (lastQueryAtMs.size() > 10_000) {
            lastQueryAtMs.clear(); // 粗粒度防膨胀，回调量极低，清空无碍
        }
        Long prev = lastQueryAtMs.put(flowId, now);
        return prev != null && (now - prev) < QUERY_THROTTLE_MS;
    }

    /**
     * 处理 ESS Webhook 回调。
     *
     * @param requestBody   原始请求体
     * @param signature     回调签名
     * @param timestamp     回调时间戳
     * @return 处理结果
     */
    /**
     * 处理 ESS Webhook 回调。
     * <p>
     * 腾讯电子签回调通知没有可靠的请求级签名机制（此前臆造的 {@code X-ESS-Signature}/{@code X-ESS-Timestamp}
     * + HMAC 方案从未匹配过腾讯实际回调，恒验签失败）。因此本服务<b>不信任回调内容</b>，仅把回调当作
     * 「去服务端权威查单」的触发器：取出 FlowId 后调用 TC3 签名的 {@link EssContractService#describeFlowStatus}
     * 拉取真实状态（其内部会更新 EssFlowRecord 并在 COMPLETED 时桥接 Contract→SIGNED）。
     * <p>
     * 安全性：伪造回调至多触发一次无害的权威查单，无法把合同推进到非真实状态。{@code signature}/{@code timestamp}
     * 参数保留仅为兼容控制器签名，不再使用。
     */
    @Transactional
    public CallbackResult handleCallback(String requestBody, String signature, String timestamp) {
        try {
            JsonNode callbackData = objectMapper.readTree(requestBody);
            String flowId = extractFlowId(callbackData);
            String eventType = extractEventType(callbackData);

            if (flowId == null || flowId.isEmpty()) {
                log.warn("回调数据缺少 FlowId，忽略");
                return CallbackResult.ignored("缺少 FlowId");
            }

            EssFlowRecord record = flowRecordRepository.findByEssFlowId(flowId).orElse(null);
            if (record == null) {
                log.warn("未找到流程记录 [flowId={}]，忽略回调", flowId);
                return CallbackResult.ignored("流程不存在: " + flowId);
            }

            // 节流：同一 FlowId 窗口内不重复触发对腾讯的权威查单（防滥用刷配额 + 合并腾讯重试）。
            if (queryThrottled(flowId)) {
                log.info("回调查单节流命中，跳过本次 [flowId={}]", flowId);
                return CallbackResult.success(record.getContractId(), flowId, extractEventType(callbackData));
            }

            // 权威同步：以服务端查单为准（内部更新 EssFlowRecord + COMPLETED 时桥接 Contract→SIGNED）。
            if (essContractService != null) {
                essContractService.describeFlowStatus(record.getContractId());
            } else {
                // 理论不可达的兜底（生产已注入 EssContractService）：退回按回调体更新。
                updateFlowStatus(record, eventType, callbackData);
                flowRecordRepository.save(record);
                if (record.getFlowStatus() == FlowStatus.COMPLETED && completionBridge != null) {
                    completionBridge.bridgeToSigned(record.getContractId(), extractPdfHash(callbackData));
                }
            }

            log.info("ESS 回调已触发权威查单 [contractId={}, flowId={}, event={}]",
                    record.getContractId(), flowId, eventType);
            return CallbackResult.success(record.getContractId(), flowId, eventType);

        } catch (Exception e) {
            log.error("处理 ESS 回调异常: {}", e.getMessage(), e);
            throw new EssFlowException("unknown", "处理回调失败: " + e.getMessage());
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

    /**
     * 从回调 payload 中尽力抽取 PdfHash 作为桥接 hint。常见字段：PdfHash / Data.PdfHash / FileHash。
     * 找不到返回 null（桥接环节会回退为空字符串占位，归档时由 SHA-256 计算补齐）。
     */
    private String extractPdfHash(JsonNode data) {
        if (data == null) return null;
        if (data.has("PdfHash")) return data.get("PdfHash").asText(null);
        if (data.has("FileHash")) return data.get("FileHash").asText(null);
        if (data.has("Data") && data.get("Data").has("PdfHash")) {
            return data.get("Data").get("PdfHash").asText(null);
        }
        return null;
    }

    private void updateFlowStatus(EssFlowRecord record, String eventType, JsonNode callbackData) {
        String dataJson = callbackData.toString();

        switch (eventType) {
            case "FlowStatusChanged", "SignComplete" -> {
                String status = callbackData.has("FlowStatus")
                        ? callbackData.get("FlowStatus").asText() : null;
                FlowStatus mapped = EssStatusMapper.map(status);
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

    private void applyStatus(EssFlowRecord record, FlowStatus status, String data) {
        switch (status) {
            case COMPLETED -> record.complete(data);
            case CANCELLED -> record.cancel();
            case REJECTED -> record.reject(data);
            case ERROR -> record.markError(data);
            default -> record.updateCallbackData(data);
        }
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
