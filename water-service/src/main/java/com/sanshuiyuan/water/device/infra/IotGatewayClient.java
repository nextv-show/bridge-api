package com.sanshuiyuan.water.device.infra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * iot-gateway-service S2S 客户端。把出水/锁定命令转发到 iot 网关的 /internal/iot/cmd/*，
 * 由网关经 MQTT 下发到设备。鉴权用 S2S shared token。
 */
@Component
public class IotGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(IotGatewayClient.class);

    private final RestTemplate restTemplate;

    @Value("${iot-gateway.base-url:http://localhost:8089}")
    private String baseUrl;

    @Value("${s2s.token:dev-s2s-shared-token}")
    private String s2sToken;

    public IotGatewayClient(RestTemplateBuilder builder) {
        this.restTemplate = builder.build();
    }

    public void start(String sn, Long sessionId, long maxLitersMilli) {
        post("/internal/iot/cmd/start", Map.of(
            "sn", sn, "session_id", sessionId, "max_liters_milli", maxLitersMilli));
    }

    public void stop(String sn, Long sessionId) {
        post("/internal/iot/cmd/stop", Map.of("sn", sn, "session_id", sessionId));
    }

    public void lock(String sn, String reason) {
        post("/internal/iot/cmd/lock", Map.of("sn", sn, "reason", reason));
    }

    public void unlock(String sn) {
        post("/internal/iot/cmd/unlock", Map.of("sn", sn, "reason", "UNLOCK"));
    }

    private void post(String path, Map<String, Object> body) {
        var headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + s2sToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        var entity = new HttpEntity<>(body, headers);
        restTemplate.postForEntity(baseUrl + path, entity, Map.class);
    }
}
