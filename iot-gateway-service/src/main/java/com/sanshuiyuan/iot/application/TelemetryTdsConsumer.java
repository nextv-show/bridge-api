package com.sanshuiyuan.iot.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanshuiyuan.iot.domain.TelemetrySampleTds;
import com.sanshuiyuan.iot.infra.repository.TelemetryTdsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * TDS（水质）遥测消费者。解析 {@code {"tds_value":120}} 并落库。
 */
@Service
public class TelemetryTdsConsumer {

    private static final Logger log = LoggerFactory.getLogger(TelemetryTdsConsumer.class);

    private final TelemetryTdsRepository repo;
    private final ObjectMapper objectMapper;

    public TelemetryTdsConsumer(TelemetryTdsRepository repo, ObjectMapper objectMapper) {
        this.repo = repo;
        this.objectMapper = objectMapper;
    }

    public void onTds(String sn, byte[] payload) {
        try {
            JsonNode json = objectMapper.readTree(payload);
            int tdsValue = json.get("tds_value").asInt();

            var sample = new TelemetrySampleTds(sn, tdsValue, LocalDateTime.now());
            repo.save(sample);
        } catch (Exception e) {
            log.error("[MQTT] Failed to parse tds telemetry from sn={}: {}", sn, e.getMessage());
        }
    }
}
