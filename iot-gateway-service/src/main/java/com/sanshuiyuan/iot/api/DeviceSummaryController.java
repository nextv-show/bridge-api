package com.sanshuiyuan.iot.api;

import com.sanshuiyuan.iot.domain.DeviceStatus;
import com.sanshuiyuan.iot.domain.TelemetrySampleFilter;
import com.sanshuiyuan.iot.domain.TelemetrySampleTds;
import com.sanshuiyuan.iot.infra.repository.DeviceAlarmRepository;
import com.sanshuiyuan.iot.infra.repository.DeviceStatusRepository;
import com.sanshuiyuan.iot.infra.repository.TelemetryFilterRepository;
import com.sanshuiyuan.iot.infra.repository.TelemetryFlowRepository;
import com.sanshuiyuan.iot.infra.repository.TelemetryTdsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * S2S 内部：设备监控汇总聚合。由 water-service 调用，用于拼装 C 端 /devices/{sn}/summary 响应。
 */
@RestController
@RequestMapping("/internal/iot/devices")
public class DeviceSummaryController {

    private static final Logger log = LoggerFactory.getLogger(DeviceSummaryController.class);

    private final DeviceStatusRepository statusRepo;
    private final TelemetryTdsRepository tdsRepo;
    private final TelemetryFlowRepository flowRepo;
    private final TelemetryFilterRepository filterRepo;
    private final DeviceAlarmRepository alarmRepo;

    public DeviceSummaryController(DeviceStatusRepository statusRepo,
                                   TelemetryTdsRepository tdsRepo,
                                   TelemetryFlowRepository flowRepo,
                                   TelemetryFilterRepository filterRepo,
                                   DeviceAlarmRepository alarmRepo) {
        this.statusRepo = statusRepo;
        this.tdsRepo = tdsRepo;
        this.flowRepo = flowRepo;
        this.filterRepo = filterRepo;
        this.alarmRepo = alarmRepo;
    }

    @GetMapping("/{sn}/summary")
    public ResponseEntity<Map<String, Object>> getSummary(@PathVariable String sn) {
        Map<String, Object> data = new LinkedHashMap<>();

        // 1. 在线状态 + last_seen_at
        DeviceStatus status = statusRepo.findBySn(sn).orElse(null);
        data.put("online", status != null && status.isOnline());
        data.put("last_seen_at", status != null ? status.getLastSeenAt() : null);

        // 2. 最新 TDS
        TelemetrySampleTds tds = tdsRepo.findTopBySnOrderBySampledAtDesc(sn).orElse(null);
        data.put("last_tds", tds != null ? tds.getTdsValue() : null);

        // 3. 近 24h 累计流量（sum(delta_milli)，毫升转升）
        LocalDateTime since24h = LocalDateTime.now().minusHours(24);
        Long totalDeltaMilli = flowRepo.sumDeltaMilliBySnSince(sn, since24h);
        double totalFlowLiters = totalDeltaMilli != null ? totalDeltaMilli / 1000.0 : 0.0;
        data.put("total_flow_liters", totalFlowLiters);

        // 4. 最新滤芯剩余寿命百分比
        TelemetrySampleFilter filter = filterRepo.findTopBySnOrderBySampledAtDesc(sn).orElse(null);
        data.put("filter_life_percent", filter != null ? filter.getLifePercent() : null);

        // 5. 近 24h 未闭环告警数
        long alarmsOpen24h = alarmRepo.countBySnAndResolvedAtIsNullAndRaisedAtAfter(sn, since24h);
        data.put("alarms_open_24h", alarmsOpen24h);

        log.debug("[Summary] sn={} online={} tds={} flow24h={}L alarms={}",
                sn, data.get("online"), data.get("last_tds"), totalFlowLiters, alarmsOpen24h);

        return ResponseEntity.ok(Map.of("code", 0, "data", data));
    }
}
