package com.sanshuiyuan.matching.request.application;

import com.sanshuiyuan.matching.request.infra.DeviceAssetGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 110 {@link CendOrderSnBindbackNotifier} 纯单测（Mockito，不依赖 DB / 网络）：
 * sn 透传 / 反查 device_assets.sn、payload 组装、cend-base-url 未配置跳过、失败降级（绝不抛出）。
 */
@ExtendWith(MockitoExtension.class)
class CendOrderSnBindbackNotifierTest {

    private static final String BASE = "http://cend:8080";
    private static final String TOKEN = "test-s2s-token";

    @Mock
    DeviceAssetGateway gateway;
    @Mock
    RestTemplate restTemplate;

    private CendOrderSnBindbackNotifier newNotifier(String baseUrl) {
        CendOrderSnBindbackNotifier n = new CendOrderSnBindbackNotifier(gateway, baseUrl, TOKEN);
        n.restTemplate = restTemplate;
        return n;
    }

    @SuppressWarnings("unchecked")
    @Test
    void snProvided_postsExpectedPayload() {
        when(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"status\":\"ok\"}"));

        newNotifier(BASE).notifyBindSn(2001L, "SN-REAL-001");

        // sn 入参有值时不查库
        verifyNoInteractions(gateway);

        ArgumentCaptor<String> urlCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<HttpEntity> entityCap = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(urlCap.capture(), entityCap.capture(), eq(String.class));

        assertThat(urlCap.getValue()).isEqualTo(BASE + "/internal/orders/bind-sn");

        HttpEntity<Map<String, Object>> entity = entityCap.getValue();
        assertThat(entity.getHeaders().getFirst("Authorization")).isEqualTo("Bearer " + TOKEN);
        Map<String, Object> body = entity.getBody();
        assertThat(body.get("device_asset_id")).isEqualTo(2001L);
        assertThat(body.get("sn")).isEqualTo("SN-REAL-001");
    }

    @SuppressWarnings("unchecked")
    @Test
    void snNull_looksUpGateway_thenPosts() {
        when(gateway.findSnById(2001L)).thenReturn("SN-LOOKED-UP");
        when(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        newNotifier(BASE).notifyBindSn(2001L, null);

        verify(gateway).findSnById(2001L);
        ArgumentCaptor<HttpEntity> entityCap = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(any(String.class), entityCap.capture(), eq(String.class));
        Map<String, Object> body = (Map<String, Object>) entityCap.getValue().getBody();
        assertThat(body.get("sn")).isEqualTo("SN-LOOKED-UP");
    }

    @Test
    void snNull_gatewayReturnsPlaceholder_skips_noPost() {
        when(gateway.findSnById(2001L)).thenReturn("SN-PENDING-H5ORDER001");

        newNotifier(BASE).notifyBindSn(2001L, null);

        verify(restTemplate, never()).postForEntity(any(String.class), any(), eq(String.class));
    }

    @Test
    void blankBaseUrl_skips_noLookupNoPost() {
        newNotifier("").notifyBindSn(2001L, "SN-REAL-001");
        verifyNoInteractions(gateway, restTemplate);
    }

    @Test
    void non2xx_doesNotThrow() {
        when(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
                .thenReturn(ResponseEntity.status(500).body("boom"));

        assertThatCode(() -> newNotifier(BASE).notifyBindSn(2001L, "SN-REAL-001"))
                .doesNotThrowAnyException();
    }

    @Test
    void restException_doesNotThrow() {
        when(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
                .thenThrow(new RestClientException("conn refused"));

        assertThatCode(() -> newNotifier(BASE).notifyBindSn(2001L, "SN-REAL-001"))
                .doesNotThrowAnyException();
    }
}
