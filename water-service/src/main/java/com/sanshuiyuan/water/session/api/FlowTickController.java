package com.sanshuiyuan.water.session.api;

import com.sanshuiyuan.water.common.ApiResponse;
import com.sanshuiyuan.water.session.application.FlowTickEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 内部 S2S 流量推送（iot-gateway 每收到 flow 样本调用）。
 * 发布 {@link FlowTickEvent} 供 WebSocket 实时下发，不落库（落库在 iot-gateway 侧）。
 */
@RestController
public class FlowTickController {

    private final ApplicationEventPublisher eventPublisher;

    public FlowTickController(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @PostMapping("/internal/water/sessions/{id}/flow-tick")
    public Map<String, Object> flowTick(@PathVariable Long id, @RequestBody FlowTickRequest req) {
        long liters = req.litersMilli() == null ? 0L : req.litersMilli();
        long delta = req.deltaMilli() == null ? 0L : req.deltaMilli();
        eventPublisher.publishEvent(new FlowTickEvent(id, liters, delta));
        return ApiResponse.ok();
    }

    public record FlowTickRequest(Long litersMilli, Long deltaMilli) {}
}
