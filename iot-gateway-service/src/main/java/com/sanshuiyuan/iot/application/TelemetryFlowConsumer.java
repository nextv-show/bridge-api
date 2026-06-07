package com.sanshuiyuan.iot.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanshuiyuan.iot.domain.TelemetrySampleFlow;
import com.sanshuiyuan.iot.infra.repository.TelemetryFlowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 流量遥测消费者。解析 {@code {"session_id":123,"liters_milli":5000,"delta_milli":100}} 并落库。
 */
@Service
public class TelemetryFlowConsumer {

    private static final Logger log = LoggerFactory.getLogger(TelemetryFlowConsumer.class);

    private final TelemetryFlowRepository repo;
    private final ObjectMapper objectMapper;
    private final WaterServiceClient waterServiceClient;

    public TelemetryFlowConsumer(TelemetryFlowRepository repo, ObjectMapper objectMapper,
                                  WaterServiceClient waterServiceClient) {
        this.repo = repo;
        this.objectMapper = objectMapper;
        this.waterServiceClient = waterServiceClient;
    }

    public void onFlow(String sn, byte[] payload) {
        try {
            JsonNode json = objectMapper.readTree(payload);
            Long sessionId = json.has("session_id") && !json.get("session_id").isNull()
                    ? json.get("session_id").asLong() : null;
            long litersMilli = json.get("liters_milli").asLong();
            long deltaMilli = json.get("delta_milli").asLong();

            var sample = new TelemetrySampleFlow(sn, sessionId, litersMilli, deltaMilli, LocalDateTime.now());
            repo.save(sample);

            // Push flow tick to water-service for WebSocket broadcast
            if (sessionId != null) {
                waterServiceClient.pushFlowTick(sessionId, litersMilli, deltaMilli);
            }
        } catch (Exception e) {
            log.error("[MQTT] Failed to parse flow telemetry from sn={}: {}", sn, e.getMessage());
        }
    }
}
