package com.sanshuiyuan.matching.request.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sanshuiyuan.matching.request.api.dto.ActivateResponse;
import com.sanshuiyuan.matching.request.application.ActivateDeviceUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 029 设备激活内部端点（service-to-service，S2S token 鉴权，不对外暴露）。
 * iot-gateway-service 在设备首个 MQTT 心跳/上线边沿调用，推进 PENDING_ACTIVATE → STAGE_1。
 *
 * <p>鉴权：{@code /internal/**} 由 SecurityConfig 的 S2sTokenFilter 守护，
 * 认 {@code Authorization: Bearer <s2s-token>}（与 logistics fulfill 调用同）。
 */
@RestController
@RequestMapping("/internal/matching")
public class ActivateController {

    private final ActivateDeviceUseCase activateDeviceUseCase;

    public ActivateController(ActivateDeviceUseCase activateDeviceUseCase) {
        this.activateDeviceUseCase = activateDeviceUseCase;
    }

    @PostMapping("/activate")
    @ResponseStatus(HttpStatus.OK)
    public ActivateResponse activate(@RequestBody ActivateBody body) {
        return activateDeviceUseCase.activate(body.sn());
    }

    // 线格式 snake_case；matching 无全局 SNAKE_CASE 策略，单字段仍显式 @JsonProperty（防 #98 同类 wire bug）。
    public record ActivateBody(@JsonProperty("sn") String sn) {}
}
