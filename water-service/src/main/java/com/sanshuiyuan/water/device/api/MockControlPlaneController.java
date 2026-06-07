package com.sanshuiyuan.water.device.api;

import com.sanshuiyuan.water.common.ApiResponse;
import com.sanshuiyuan.water.device.domain.DeviceControlEvent;
import com.sanshuiyuan.water.device.infra.DeviceControlEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Mock 控制面：写入 LOCK_CHANGED 事件用于测试，由 DevicePermissionProjector 轮询消费。
 * 生产环境应移除或加固。
 */
@RestController
@RequestMapping("/api/w/internal/mock/control")
public class MockControlPlaneController {

    private static final Logger log = LoggerFactory.getLogger(MockControlPlaneController.class);

    private final DeviceControlEventRepository repo;

    public MockControlPlaneController(DeviceControlEventRepository repo) {
        this.repo = repo;
    }

    @PostMapping("/lock")
    public Map<String, Object> lockDevice(@RequestBody Map<String, Object> body) {
        String sn = (String) body.get("sn");
        String reason = (String) body.getOrDefault("reason", "LOCKED");
        return writeEvent(sn, false, reason);
    }

    @PostMapping("/unlock")
    public Map<String, Object> unlockDevice(@RequestBody Map<String, Object> body) {
        String sn = (String) body.get("sn");
        return writeEvent(sn, true, "UNLOCK");
    }

    private Map<String, Object> writeEvent(String sn, boolean canDispense, String reason) {
        DeviceControlEvent event = new DeviceControlEvent(sn, "LOCK_CHANGED", canDispense, reason);
        repo.save(event);
        log.info("[MockControl] Wrote LOCK_CHANGED sn={} can_dispense={} reason={}", sn, canDispense, reason);
        return ApiResponse.ok(Map.of("event_id", event.getId(), "sn", sn, "can_dispense", canDispense));
    }
}
