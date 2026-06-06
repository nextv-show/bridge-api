package com.sanshuiyuan.iot.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 内部命令下发 API（S2S，仅 /internal/**）。把命令通过 MQTT 发到 {@code device/{sn}/cmd/*}。
 */
@RestController
@RequestMapping("/internal/iot/cmd")
public class InternalCommandController {

    private static final Logger log = LoggerFactory.getLogger(InternalCommandController.class);

    private final Mqtt5AsyncClient mqttClient;
    private final ObjectMapper objectMapper;

    public InternalCommandController(Mqtt5AsyncClient mqttClient, ObjectMapper objectMapper) {
        this.mqttClient = mqttClient;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> start(@RequestBody Map<String, Object> body) {
        String sn = (String) body.get("sn");
        publish("device/" + sn + "/cmd/start", body);
        return ResponseEntity.ok(Map.of("code", 0));
    }

    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stop(@RequestBody Map<String, Object> body) {
        String sn = (String) body.get("sn");
        publish("device/" + sn + "/cmd/stop", body);
        return ResponseEntity.ok(Map.of("code", 0));
    }

    @PostMapping("/lock")
    public ResponseEntity<Map<String, Object>> lock(@RequestBody Map<String, Object> body) {
        String sn = (String) body.get("sn");
        publish("device/" + sn + "/cmd/lock", body);
        return ResponseEntity.ok(Map.of("code", 0));
    }

    @PostMapping("/unlock")
    public ResponseEntity<Map<String, Object>> unlock(@RequestBody Map<String, Object> body) {
        String sn = (String) body.get("sn");
        publish("device/" + sn + "/cmd/unlock", body);
        return ResponseEntity.ok(Map.of("code", 0));
    }

    private void publish(String topic, Object payload) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(payload);
            mqttClient.publishWith()
                    .topic(topic)
                    .payload(bytes)
                    .qos(MqttQos.AT_LEAST_ONCE)
                    .send()
                    .get(3, TimeUnit.SECONDS);
            log.info("[MQTT] Published to {}", topic);
        } catch (Exception e) {
            log.error("[MQTT] Failed to publish to {}: {}", topic, e.getMessage());
            throw new RuntimeException("MQTT publish failed", e);
        }
    }
}
