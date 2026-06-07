package com.sanshuiyuan.iot.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanshuiyuan.iot.domain.TelemetrySampleFilter;
import com.sanshuiyuan.iot.infra.repository.TelemetryFilterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 滤芯寿命遥测消费者。解析 {@code {"life_percent":80}} 并落库；同时触发滤芯低量阈值检查。
 */
@Service
public class TelemetryFilterConsumer {

    private static final Logger log = LoggerFactory.getLogger(TelemetryFilterConsumer.class);

    private final TelemetryFilterRepository repo;
    private final ObjectMapper objectMapper;
    private final AlarmThresholdEvaluator thresholdEvaluator;

    public TelemetryFilterConsumer(TelemetryFilterRepository repo, ObjectMapper objectMapper,
                                   AlarmThresholdEvaluator thresholdEvaluator) {
        this.repo = repo;
        this.objectMapper = objectMapper;
        this.thresholdEvaluator = thresholdEvaluator;
    }

    public void onFilter(String sn, byte[] payload) {
        try {
            JsonNode json = objectMapper.readTree(payload);
            short lifePercent = (short) json.get("life_percent").asInt();

            var sample = new TelemetrySampleFilter(sn, lifePercent, LocalDateTime.now());
            repo.save(sample);

            // 落库后即时评估滤芯低量阈值
            thresholdEvaluator.checkFilterLife(sn);
        } catch (Exception e) {
            log.error("[MQTT] Failed to parse filter telemetry from sn={}: {}", sn, e.getMessage());
        }
    }
}
