package com.sanshuiyuan.logistics.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanshuiyuan.logistics.application.AdvanceStateUseCase;
import com.sanshuiyuan.logistics.domain.LogisticsStatus;
import com.sanshuiyuan.logistics.infra.webhook.SignatureVerifier;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

/**
 * D.3.3 Webhook 回调接收（V1 骨架，第三方接入留 V2）。
 * 签名校验 + external_event_id 幂等。未配置的 provider 返回 404。
 */
@RestController
@RequestMapping("/logistics/webhook")
public class WebhookController {

    private final SignatureVerifier signatureVerifier;
    private final AdvanceStateUseCase advanceStateUseCase;

    public WebhookController(SignatureVerifier signatureVerifier,
                             AdvanceStateUseCase advanceStateUseCase) {
        this.signatureVerifier = signatureVerifier;
        this.advanceStateUseCase = advanceStateUseCase;
    }

    @PostMapping("/{provider}")
    public ResponseEntity<Map<String, Object>> handle(
            @PathVariable String provider,
            HttpServletRequest request) throws IOException {

        if (!signatureVerifier.isConfigured(provider)) {
            throw LogisticsApiException.notFound("PROVIDER_NOT_CONFIGURED", "未配置的物流服务商: " + provider);
        }

        // 读取原始 body 做签名校验
        String rawBody = new String(request.getInputStream().readAllBytes(),
                request.getCharacterEncoding() != null ? request.getCharacterEncoding() : "UTF-8");
        String signature = request.getHeader("X-Webhook-Signature");

        if (signature == null || !signatureVerifier.verify(provider, rawBody, signature)) {
            throw LogisticsApiException.forbidden("INVALID_SIGNATURE", "签名校验失败");
        }

        // 解析 webhook payload
        var payload = new ObjectMapper().readValue(rawBody, Map.class);
        long logisticsOrderId = ((Number) payload.get("logistics_order_id")).longValue();
        String statusStr = (String) payload.get("status");
        LogisticsStatus toStatus = LogisticsStatus.valueOf(statusStr);
        String externalEventId = (String) payload.get("external_event_id");
        String note = (String) payload.getOrDefault("note", null);

        var order = advanceStateUseCase.advance(
                logisticsOrderId, toStatus, note, externalEventId);

        return ResponseEntity.ok(Map.of(
                "id", order.getId(),
                "status", order.getStatus().name()
        ));
    }
}
