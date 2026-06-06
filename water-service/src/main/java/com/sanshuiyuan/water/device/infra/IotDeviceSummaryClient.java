package com.sanshuiyuan.water.device.infra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * S2S 客户端：调用 iot-gateway /internal/iot/devices/{sn}/summary，
 * 拉取聚合后的设备遥测，用于 C 端展示。鉴权与 IotGatewayClient 一致，使用 Bearer S2S token
 * （iot-gateway 的 S2sTokenFilter 仅识别 Authorization: Bearer）。
 */
@Component
public class IotDeviceSummaryClient {

    private static final Logger log = LoggerFactory.getLogger(IotDeviceSummaryClient.class);

    private final RestTemplate restTemplate;

    @Value("${iot-gateway.base-url:http://localhost:8089}")
    private String baseUrl;

    @Value("${s2s.token:dev-s2s-shared-token}")
    private String s2sToken;

    public IotDeviceSummaryClient(RestTemplateBuilder builder) {
        this.restTemplate = builder.build();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getSummary(String sn) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + s2sToken);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl + "/internal/iot/devices/" + sn + "/summary",
                HttpMethod.GET, entity, Map.class);

            Map<String, Object> body = resp.getBody();
            if (body != null && Integer.valueOf(0).equals(body.get("code"))) {
                return (Map<String, Object>) body.get("data");
            }
            return Map.of();
        } catch (Exception e) {
            log.warn("[S2S] device summary failed sn={}: {}", sn, e.getMessage());
            return Map.of();
        }
    }
}
