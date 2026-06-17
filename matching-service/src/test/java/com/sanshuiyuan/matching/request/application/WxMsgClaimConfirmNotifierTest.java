package com.sanshuiyuan.matching.request.application;

import com.sanshuiyuan.matching.request.application.ClaimConfirmNotifier.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
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
 * P1-2 {@link WxMsgClaimConfirmNotifier} 纯单测（Mockito，不依赖 DB / 网络）：
 * 反查 openid+channel、文案组装、通道路由（channel 透传）与失败降级（绝不抛出）。
 */
@ExtendWith(MockitoExtension.class)
class WxMsgClaimConfirmNotifierTest {

    private static final String BASE = "http://cend:8080";
    private static final String TOKEN = "test-s2s-token";
    private static final String SQL = "SELECT openid, channel FROM users WHERE id = ?";

    @Mock
    JdbcTemplate jdbc;
    @Mock
    RestTemplate restTemplate;

    private WxMsgClaimConfirmNotifier newNotifier(String baseUrl) {
        WxMsgClaimConfirmNotifier n = new WxMsgClaimConfirmNotifier(jdbc, baseUrl, TOKEN);
        n.restTemplate = restTemplate;
        return n;
    }

    @Test
    void ownerNull_skips_noQueryNoPost() {
        newNotifier(BASE).remind(42L, null, Stage.SOFT);
        verifyNoInteractions(jdbc, restTemplate);
    }

    @Test
    void blankBaseUrl_skips_noQueryNoPost() {
        newNotifier("").remind(42L, 7L, Stage.SOFT);
        verifyNoInteractions(jdbc, restTemplate);
    }

    @Test
    void openidEmpty_skips_noPost() {
        when(jdbc.queryForList(SQL, 7L)).thenReturn(List.of());

        newNotifier(BASE).remind(42L, 7L, Stage.SOFT);

        verify(restTemplate, never()).postForEntity(any(String.class), any(), eq(String.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void softStage_postsExpectedPayload() {
        when(jdbc.queryForList(SQL, 7L))
                .thenReturn(List.of(Map.of("openid", "openid-abc", "channel", "WECHAT_H5")));
        when(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"status\":\"ok\"}"));

        newNotifier(BASE).remind(42L, 7L, Stage.SOFT);

        ArgumentCaptor<String> urlCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<HttpEntity> entityCap = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(urlCap.capture(), entityCap.capture(), eq(String.class));

        assertThat(urlCap.getValue()).isEqualTo(BASE + "/internal/wxmsg/claim-reminder");

        HttpEntity<Map<String, Object>> entity = entityCap.getValue();
        assertThat(entity.getHeaders().getFirst("Authorization")).isEqualTo("Bearer " + TOKEN);
        Map<String, Object> body = entity.getBody();
        assertThat(body.get("openid")).isEqualTo("openid-abc");
        assertThat(body.get("request_id")).isEqualTo(42L);
        assertThat(body.get("stage")).isEqualTo("SOFT");
        assertThat(body.get("stage_label")).isEqualTo("温馨提醒（12小时）");
        assertThat(body.get("deadline_display")).isEqualTo("请在 12 小时内确认");
        assertThat(body.get("channel")).isEqualTo("WECHAT_H5");
    }

    @SuppressWarnings("unchecked")
    @Test
    void finalStage_usesFinalCopy() {
        when(jdbc.queryForList(SQL, 7L))
                .thenReturn(List.of(Map.of("openid", "openid-abc", "channel", "WECHAT_H5")));
        when(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        newNotifier(BASE).remind(42L, 7L, Stage.FINAL);

        ArgumentCaptor<HttpEntity> entityCap = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(any(String.class), entityCap.capture(), eq(String.class));
        Map<String, Object> body = (Map<String, Object>) entityCap.getValue().getBody();
        assertThat(body.get("stage")).isEqualTo("FINAL");
        assertThat(body.get("stage_label")).isEqualTo("最后提醒（即将自动释放）");
        assertThat(body.get("deadline_display")).isEqualTo("请立即确认");
    }

    @SuppressWarnings("unchecked")
    @Test
    void miniappChannel_passesWechatMp() {
        when(jdbc.queryForList(SQL, 7L))
                .thenReturn(List.of(Map.of("openid", "openid-mp", "channel", "WECHAT_MP")));
        when(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        newNotifier(BASE).remind(42L, 7L, Stage.SOFT);

        ArgumentCaptor<HttpEntity> entityCap = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(any(String.class), entityCap.capture(), eq(String.class));
        Map<String, Object> body = (Map<String, Object>) entityCap.getValue().getBody();
        assertThat(body.get("openid")).isEqualTo("openid-mp");
        assertThat(body.get("channel")).isEqualTo("WECHAT_MP");
    }

    @SuppressWarnings("unchecked")
    @Test
    void h5Channel_passesWechatH5() {
        when(jdbc.queryForList(SQL, 7L))
                .thenReturn(List.of(Map.of("openid", "openid-h5", "channel", "WECHAT_H5")));
        when(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        newNotifier(BASE).remind(42L, 7L, Stage.SOFT);

        ArgumentCaptor<HttpEntity> entityCap = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(any(String.class), entityCap.capture(), eq(String.class));
        Map<String, Object> body = (Map<String, Object>) entityCap.getValue().getBody();
        assertThat(body.get("channel")).isEqualTo("WECHAT_H5");
    }

    @Test
    void non2xx_doesNotThrow() {
        when(jdbc.queryForList(SQL, 7L))
                .thenReturn(List.of(Map.of("openid", "openid-abc", "channel", "WECHAT_H5")));
        when(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
                .thenReturn(ResponseEntity.status(500).body("boom"));

        assertThatCode(() -> newNotifier(BASE).remind(42L, 7L, Stage.SOFT)).doesNotThrowAnyException();
    }

    @Test
    void restException_doesNotThrow() {
        when(jdbc.queryForList(SQL, 7L))
                .thenReturn(List.of(Map.of("openid", "openid-abc", "channel", "WECHAT_H5")));
        when(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
                .thenThrow(new RestClientException("conn refused"));

        assertThatCode(() -> newNotifier(BASE).remind(42L, 7L, Stage.SOFT)).doesNotThrowAnyException();
    }
}
