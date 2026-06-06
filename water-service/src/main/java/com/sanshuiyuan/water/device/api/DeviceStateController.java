package com.sanshuiyuan.water.device.api;

import com.sanshuiyuan.water.device.domain.DevicePermission;
import com.sanshuiyuan.water.device.infra.DevicePermissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * C 端设备状态查询（stub）。Phase D（WaterSession 就绪）后补全实时出水状态。
 */
@RestController
@RequestMapping("/api/w/devices")
public class DeviceStateController {

    private static final Logger log = LoggerFactory.getLogger(DeviceStateController.class);

    private final DevicePermissionRepository permRepo;

    public DeviceStateController(DevicePermissionRepository permRepo) {
        this.permRepo = permRepo;
    }

    @GetMapping("/{sn}/state")
    public ResponseEntity<Map<String, Object>> getDeviceState(@PathVariable String sn) {
        var permOpt = permRepo.findBySn(sn);
        return ResponseEntity.ok(Map.of("code", 0, "data", Map.of(
            "sn", sn,
            "can_dispense", permOpt.map(DevicePermission::isCanDispense).orElse(false),
            "locked_reason", permOpt.map(DevicePermission::getLockedReason).map(Enum::name).orElse("NOT_INSTALLED")
        )));
    }
}
