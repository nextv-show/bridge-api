package com.sanshuiyuan.settlement.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * S2S 客户端：向 iot-gateway 拉取设备运营摘要（/internal/iot/devices/{sn}/summary）。
 *
 * 小程序用 H5 JWT，不能直连 iot-gateway 的 S2S 端点；本 client 由 settlement-service 的 BFF
 * 端点 {@code GET /api/s/owner/devices/{sn}/ops-summary} 调用，带 S2S Bearer token 转发。
 * 任何异常都兜底为设备离线（online=false），不向上抛错。
 */
@Component
public class IotOpsClient {

    private static final Logger log = LoggerFactory.getLogger(IotOpsClient.class);

    private final RestTemplate restTemplate;
    private final String iotBaseUrl;
    private final String s2sToken;

    public IotOpsClient(@Value("${iot.base-url:http://localhost:8089}") String iotBaseUrl,
                        @Value("${s2s.token:dev-s2s-shared-token}") String s2sToken) {
        // BFF 在请求路径上同步调用，必须设超时——否则 iot-gateway 挂起时小程序请求线程无限阻塞，
        // catch 的兜底退路（online=false）永远走不到。
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2_000);  // 2s 连接
        factory.setReadTimeout(3_000);     // 3s 读取
        this.restTemplate = new RestTemplate(factory);
        this.iotBaseUrl = iotBaseUrl;
        this.s2sToken = s2sToken;
    }

    /**
     * 拉取设备运营摘要的 data 字段。失败/无数据时返回 {@code {"online": false}} 兜底。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchSummary(String sn) {
        String url = iotBaseUrl + "/internal/iot/devices/" + sn + "/summary";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(s2sToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            Map<String, Object> body = response.getBody();
            if (body != null && body.get("data") instanceof Map) {
                return (Map<String, Object>) body.get("data");
            }
            return Map.of("online", false);
        } catch (Exception e) {
            log.warn("[iot-ops] fetchSummary failed sn={} err={}", sn, e.getMessage());
            return Map.of("online", false);
        }
    }
}
