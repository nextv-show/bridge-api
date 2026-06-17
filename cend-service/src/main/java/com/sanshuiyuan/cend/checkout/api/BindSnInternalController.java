package com.sanshuiyuan.cend.checkout.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sanshuiyuan.cend.checkout.application.BindSnUseCase;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 110 内部 SN 回写端点（service-to-service，S2S token 鉴权，不对外暴露）。
 * matching-service 的 CendOrderSnBindbackNotifier 在设备履约/激活节点调用，
 * 把真实 SN 回写到 h5_orders.sn（替换支付时写入的占位符）。
 */
@RestController
@RequestMapping("/internal/orders")
public class BindSnInternalController {

    private final BindSnUseCase bindSnUseCase;

    public BindSnInternalController(BindSnUseCase bindSnUseCase) {
        this.bindSnUseCase = bindSnUseCase;
    }

    @PostMapping("/bind-sn")
    public Map<String, Object> bindSn(@RequestBody BindSnBody body) {
        boolean updated = bindSnUseCase.tryBindSn(body.deviceAssetId(), body.sn());
        return updated
                ? Map.of("status", "ok")
                : Map.of("status", "noop", "reason", "sn 为空/占位符或 h5_orders.sn 非占位符");
    }

    // 线格式为 snake_case（matching CendOrderSnBindbackNotifier 发送）；cend 无全局 SNAKE_CASE 策略，
    // 故显式 @JsonProperty 映射。
    public record BindSnBody(
            @JsonProperty("device_asset_id") long deviceAssetId,
            @JsonProperty("sn") String sn) {}
}
