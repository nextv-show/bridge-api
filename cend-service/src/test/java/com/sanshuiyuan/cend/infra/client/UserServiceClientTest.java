package com.sanshuiyuan.cend.infra.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * spec 012 T12.5：UserServiceClient 单测 —— 正常解析、S2S header/body 透传、失败降级不抛出。
 */
@ExtendWith(MockitoExtension.class)
class UserServiceClientTest {

    @Mock RestTemplate restTemplate;

    private static final String BASE = "http://user-service:8081";
    private static final String TOKEN = "test-s2s-token";

    private UserServiceClient client() {
        return new UserServiceClient(restTemplate, BASE, TOKEN);
    }

    @Test
    void syncH5_success_parsesResultAndSendsS2sHeaderAndBody() {
        when(restTemplate.postForObject(eq(BASE + "/internal/users/sync-h5"), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(Map.of("userId", 100, "isNew", true, "inviterBound", true));

        UserServiceClient.SyncH5Result result = client().syncH5("openid-x", "union-x", 9L);

        assertThat(result).isNotNull();
        assertThat(result.userId()).isEqualTo(100L);
        assertThat(result.isNew()).isTrue();
        assertThat(result.inviterBound()).isTrue();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor =
                ArgumentCaptor.forClass(HttpEntity.class);
        org.mockito.Mockito.verify(restTemplate)
                .postForObject(eq(BASE + "/internal/users/sync-h5"), captor.capture(), eq(Map.class));
        HttpEntity<Map<String, Object>> sent = captor.getValue();
        assertThat(sent.getHeaders().getFirst("X-S2S-Token")).isEqualTo(TOKEN);
        assertThat(sent.getBody()).containsEntry("openid", "openid-x")
                .containsEntry("unionid", "union-x")
                .containsEntry("inviterId", 9L);
    }

    @Test
    void syncH5_restClientException_returnsNullNotThrow() {
        when(restTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RestClientException("user-service unavailable"));

        UserServiceClient c = client();
        assertThatCode(() -> {
            UserServiceClient.SyncH5Result r = c.syncH5("openid-y", null, null);
            assertThat(r).isNull();
        }).doesNotThrowAnyException();
    }

    @Test
    void syncH5_nullBody_returnsNull() {
        when(restTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(null);

        assertThat(client().syncH5("openid-z", null, null)).isNull();
    }
}
