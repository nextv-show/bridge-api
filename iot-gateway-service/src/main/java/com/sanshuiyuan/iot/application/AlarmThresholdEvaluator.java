package com.sanshuiyuan.iot.application;

import com.sanshuiyuan.iot.domain.AlarmType;
import com.sanshuiyuan.iot.domain.DeviceAlarm;
import com.sanshuiyuan.iot.domain.Severity;
import com.sanshuiyuan.iot.infra.repository.DeviceAlarmRepository;
import com.sanshuiyuan.iot.infra.repository.DeviceStatusRepository;
import com.sanshuiyuan.iot.infra.repository.TelemetryFilterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警阈值评估器。定时巡检离线设备，并在收到滤芯遥测时即时评估滤芯低量。
 * 通过复用 {@link AlarmConsumer#onAlarm} 走统一的落库 + outbox 路径。
 */
@Service
public class AlarmThresholdEvaluator {

    private static final Logger log = LoggerFactory.getLogger(AlarmThresholdEvaluator.class);

    private final DeviceStatusRepository statusRepo;
    private final TelemetryFilterRepository filterRepo;
    private final DeviceAlarmRepository alarmRepo;
    private final AlarmConsumer alarmConsumer;

    @Value("${alarm.threshold.offline-minutes:30}")
    private int offlineThresholdMinutes;

    @Value("${alarm.threshold.filter-life-percent:10}")
    private int filterLowThreshold;

    public AlarmThresholdEvaluator(DeviceStatusRepository statusRepo, TelemetryFilterRepository filterRepo,
                                   DeviceAlarmRepository alarmRepo, AlarmConsumer alarmConsumer) {
        this.statusRepo = statusRepo;
        this.filterRepo = filterRepo;
        this.alarmRepo = alarmRepo;
        this.alarmConsumer = alarmConsumer;
    }

    @Scheduled(fixedDelayString = "${alarm.evaluator.interval-ms:60000}")
    public void evaluate() {
        // 离线巡检：last_seen_at < now - offlineThresholdMinutes（完整实现待 Phase D 设备清单就绪后逐设备迭代）
        var cutoff = LocalDateTime.now().minusMinutes(offlineThresholdMinutes);
        log.debug("[AlarmEvaluator] Running threshold evaluation, offline cutoff={}", cutoff);
    }

    /** 即时检查某设备的滤芯寿命，低于阈值且无未闭环 FILTER_LOW 时升一条告警。 */
    public void checkFilterLife(String sn) {
        filterRepo.findTopBySnOrderBySampledAtDesc(sn).ifPresent(sample -> {
            if (sample.getLifePercent() < filterLowThreshold) {
                raiseAlarmIfNotOpen(sn, AlarmType.FILTER_LOW, Severity.WARN,
                    "Filter life at " + sample.getLifePercent() + "%");
            }
        });
    }

    private void raiseAlarmIfNotOpen(String sn, AlarmType type, Severity severity, String message) {
        List<DeviceAlarm> openAlarms = alarmRepo.findBySnAndResolvedAtIsNullOrderByRaisedAtDesc(sn);
        boolean alreadyOpen = openAlarms.stream()
                .anyMatch(a -> a.getAlarmType() == type);
        if (!alreadyOpen) {
            var payload = "{\"message\":\"" + message + "\"}";
            String alarmJson = "{\"alarm_type\":\"" + type.name()
                    + "\",\"severity\":\"" + severity.name()
                    + "\",\"payload\":" + payload + "}";
            alarmConsumer.onAlarm(sn, alarmJson.getBytes(StandardCharsets.UTF_8));
        }
    }
}
