package com.sanshuiyuan.settlement.api;

import com.sanshuiyuan.settlement.application.IotOpsClient;
import com.sanshuiyuan.settlement.auth.SettlementSubjectResolver;
import com.sanshuiyuan.settlement.infra.asset.DeviceAssetRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 设备运营数据 BFF：聚合 iot-gateway 的设备运营摘要给小程序。
 *
 * 小程序用 H5 JWT，不能直连 iot-gateway 的 S2S 端点 /internal/iot/devices/{sn}/summary。
 * 本端点用 H5 JWT 鉴权（/api/s/** 已由 H5JwtFilter 守护），先按 device_assets 校验设备归属，
 * 再以 S2S 身份转发到 iot-gateway 并透传其 data 字段。
 */
@RestController
@RequestMapping("/api/s/owner/devices")
public class OwnerDeviceOpsController {

    private final DeviceAssetRepository deviceAssetRepository;
    private final SettlementSubjectResolver subjectResolver;
    private final IotOpsClient iotOpsClient;

    public OwnerDeviceOpsController(DeviceAssetRepository deviceAssetRepository,
                                    SettlementSubjectResolver subjectResolver,
                                    IotOpsClient iotOpsClient) {
        this.deviceAssetRepository = deviceAssetRepository;
        this.subjectResolver = subjectResolver;
        this.iotOpsClient = iotOpsClient;
    }

    @GetMapping("/{sn}/ops-summary")
    public ResponseEntity<Map<String, Object>> getOpsSummary(@PathVariable String sn, Authentication auth) {
        Long userId = subjectResolver.resolveUserId(auth.getName());
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "UNAUTHORIZED"));
        }

        // 归属校验：设备必须属于当前用户
        if (deviceAssetRepository.findBySnAndUserId(sn, userId).isEmpty()) {
            return ResponseEntity.status(403).body(Map.of("code", 403, "message", "FORBIDDEN"));
        }

        Map<String, Object> data = iotOpsClient.fetchSummary(sn);
        return ResponseEntity.ok(Map.of("code", 0, "data", data));
    }

    @GetMapping("/{sn}/cumulative-flow")
    public ResponseEntity<Map<String, Object>> getCumulativeFlow(@PathVariable String sn, Authentication auth) {
        Long userId = subjectResolver.resolveUserId(auth.getName());
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "UNAUTHORIZED"));
        }

        // 归属校验：设备必须属于当前用户
        if (deviceAssetRepository.findBySnAndUserId(sn, userId).isEmpty()) {
            return ResponseEntity.status(403).body(Map.of("code", 403, "message", "FORBIDDEN"));
        }

        Map<String, Object> data = iotOpsClient.fetchCumulativeFlow(sn);
        return ResponseEntity.ok(Map.of("code", 0, "data", data));
    }
}
