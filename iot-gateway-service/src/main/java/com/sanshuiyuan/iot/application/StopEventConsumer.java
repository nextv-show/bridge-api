package com.sanshuiyuan.iot.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Device stop event consumer. Parses MQTT stop event and calls water-service to settle the session.
 */
@Service
public class StopEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(StopEventConsumer.class);

    private final WaterServiceClient waterServiceClient;
    private final ObjectMapper objectMapper;

    public StopEventConsumer(WaterServiceClient waterServiceClient, ObjectMapper objectMapper) {
        this.waterServiceClient = waterServiceClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Handle device stop event from MQTT topic device/{sn}/event/stop.
     * Expected payload: {"session_id": 123, "liters_milli": 5000, "reason": "USER_STOP"}
     */
    public void onStop(String sn, byte[] payload) {
        try {
            JsonNode json = objectMapper.readTree(payload);
            long sessionId = json.get("session_id").asLong();
            long litersMilli = json.has("liters_milli") ? json.get("liters_milli").asLong() : 0;
            String reason = json.has("reason") ? json.get("reason").asText() : "USER_STOP";

            log.info("[MQTT] Stop event sn={} sessionId={} liters={} reason={}", sn, sessionId, litersMilli, reason);
            waterServiceClient.settleSession(sessionId, litersMilli, reason);
        } catch (Exception e) {
            log.error("[MQTT] Failed to handle stop event from sn={}: {}", sn, e.getMessage());
        }
    }
}
