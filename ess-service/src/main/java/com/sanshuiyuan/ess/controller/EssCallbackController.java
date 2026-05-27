package com.sanshuiyuan.ess.controller;

import com.sanshuiyuan.ess.service.EssCallbackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * ESS Webhook 回调端点。
 * <p>
 * 接收腾讯电子签的 Webhook 回调通知。
 * 路径 /api/internal/ess/callback，在 SecurityConfig 中已配置为 permitAll。
 */
@RestController
@RequestMapping("/api/internal/ess")
public class EssCallbackController {

    private static final Logger log = LoggerFactory.getLogger(EssCallbackController.class);

    private final EssCallbackService callbackService;

    public EssCallbackController(EssCallbackService callbackService) {
        this.callbackService = callbackService;
    }

    /**
     * 接收 ESS Webhook 回调。
     *
     * @param requestBody 原始请求体
     * @param signature   回调签名（X-ESS-Signature 头）
     * @param timestamp   回调时间戳（X-ESS-Timestamp 头）
     * @return 处理结果
     */
    @PostMapping("/callback")
    public ResponseEntity<Map<String, Object>> handleCallback(
            @RequestBody String requestBody,
            @RequestHeader(value = "X-ESS-Signature", required = false) String signature,
            @RequestHeader(value = "X-ESS-Timestamp", required = false) String timestamp) {

        log.info("收到 ESS 回调请求, bodyLength={}", requestBody != null ? requestBody.length() : 0);

        try {
            var result = callbackService.handleCallback(requestBody, signature, timestamp);

            if (result.success()) {
                return ResponseEntity.ok(Map.of(
                        "code", 0,
                        "message", "OK",
                        "contractId", result.contractId(),
                        "flowId", result.flowId(),
                        "eventType", result.eventType()
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                        "code", 0,
                        "message", "Ignored: " + result.message()
                ));
            }

        } catch (Exception e) {
            log.error("处理 ESS 回调失败: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                    "code", -1,
                    "message", "处理失败: " + e.getMessage()
            ));
        }
    }
}
