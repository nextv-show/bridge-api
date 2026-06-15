package com.sanshuiyuan.iot.application;

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
 * 029 S2S client: iot-gateway -> matching-service。
 * 设备首个心跳/上线边沿时调 matching 推进 device_assets PENDING_ACTIVATE → STAGE_1（设备激活）。
 *
 * <p>best-effort（与本服务 {@link WaterServiceClient} 同风格）：失败仅记日志——上线边沿会复触发、
 * matching 侧 CAS 幂等（已 STAGE_1 / 非 PENDING_ACTIVATE 命中 0 行 no-op），共同提供重试语义。
 */
@Component
public class MatchingActivationClient {

    private static final Logger log = LoggerFactory.getLogger(MatchingActivationClient.class);

    private final RestTemplate restTemplate;

    @Value("${s2s.token:dev-s2s-shared-token}")
    private String s2sToken;

    public MatchingActivationClient(RestTemplateBuilder builder,
                                    @Value("${matching.internal-url:http://localhost:8086}") String matchingUrl) {
        this.restTemplate = builder.rootUri(matchingUrl).build();
    }

    /** 触发设备激活。sn 由 MQTT topic 提取，对应 device_assets.sn。 */
    public void activate(String sn) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // matching S2sTokenFilter 认 `Authorization: Bearer <token>`（与 logistics 同，非裸 token）。
            headers.set("Authorization", "Bearer " + s2sToken);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(Map.of("sn", sn), headers);
            restTemplate.postForEntity("/internal/matching/activate", entity, Map.class);
            log.info("[S2S] activate sent sn={}", sn);
        } catch (Exception e) {
            log.warn("[S2S] activate failed sn={}: {}（上线边沿会复触发；matching 幂等兜底）", sn, e.getMessage());
        }
    }
}
