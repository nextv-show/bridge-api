package com.sanshuiyuan.matching.request.api;

import com.sanshuiyuan.matching.request.application.FulfillUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
        fulfillUseCase.fulfill(body.requestId, body.deviceAssetId, body.logisticsOrderId);
        return ResponseEntity.ok(Map.of(
                "status", "FULFILLED",
                "request_id", body.requestId
        ));
    }

    public record FulfillBody(long requestId, long deviceAssetId, long logisticsOrderId) {}
}
