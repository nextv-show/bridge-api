package com.sanshuiyuan.iot.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * S2S client: iot-gateway -> water-service.
 * Used for stop-event callback and flow-tick push.
 */
@Component
public class WaterServiceClient {

    private static final Logger log = LoggerFactory.getLogger(WaterServiceClient.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${s2s.token:dev-s2s-shared-token}")
    private String s2sToken;

    public WaterServiceClient(RestTemplateBuilder builder, ObjectMapper objectMapper) {
        this.restTemplate = builder
                .rootUri("http://localhost:8088")
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * Notify water-service to settle a session (stop event callback).
     */
    public void settleSession(long sessionId, long litersMilli, String reason) {
        try {
            HttpHeaders headers = s2sHeaders();
            Map<String, Object> body = Map.of(
                    "liters_milli", litersMilli,
                    "reason", reason
            );
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            restTemplate.postForEntity("/internal/water/sessions/" + sessionId + "/settle", entity, Map.class);
            log.info("[S2S] settle callback sent sessionId={} liters={} reason={}", sessionId, litersMilli, reason);
        } catch (Exception e) {
            log.error("[S2S] settle callback failed sessionId={}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * Push flow tick to water-service for real-time WebSocket broadcast.
     */
    public void pushFlowTick(long sessionId, long litersMilli, long deltaMilli) {
        try {
            HttpHeaders headers = s2sHeaders();
            Map<String, Object> body = Map.of(
                    "liters_milli", litersMilli,
                    "delta_milli", deltaMilli
            );
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            restTemplate.postForEntity("/internal/water/sessions/" + sessionId + "/flow-tick", entity, Map.class);
        } catch (Exception e) {
            log.warn("[S2S] flow-tick push failed sessionId={}: {}", sessionId, e.getMessage());
        }
    }

    private HttpHeaders s2sHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-S2S-Token", s2sToken);
        return headers;
    }
}
