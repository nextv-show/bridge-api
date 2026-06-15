package com.sanshuiyuan.matching.request.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sanshuiyuan.matching.request.application.FulfillUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * D.4 内部履约端点（service-to-service，S2S token 鉴权，不对外暴露）。
 * logistics-service 在 INSTALLED 推进后调用。
 */
@RestController
@RequestMapping("/internal/matching")
public class FulfillController {

    private final FulfillUseCase fulfillUseCase;

    public FulfillController(FulfillUseCase fulfillUseCase) {
        this.fulfillUseCase = fulfillUseCase;
    }

    @PostMapping("/fulfill")
    public ResponseEntity<Map<String, Object>> fulfill(@RequestBody FulfillBody body) {
        // requestId == null → SELF_USE 设备（无匹配需求），fulfill() 内部路由到 fulfillSelfUse
        fulfillUseCase.fulfill(body.requestId, body.deviceAssetId, body.logisticsOrderId);
        // LinkedHashMap：request_id 对 SELF_USE 为 null，Map.of 不允许 null value
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "FULFILLED");
        resp.put("request_id", body.requestId);
        return ResponseEntity.ok(resp);
    }

    // S2S 线格式为 snake_case（logistics InstalledEventPublisher 发送），matching 无全局 SNAKE_CASE 策略，
    // 故须显式 @JsonProperty 映射，否则 requestId=null/deviceAssetId=0 会被误判为 SELF_USE 路径。
    public record FulfillBody(
            @JsonProperty("request_id") Long requestId,
            @JsonProperty("device_asset_id") long deviceAssetId,
            @JsonProperty("logistics_order_id") long logisticsOrderId) {}
}
