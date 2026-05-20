package com.sanshuiyuan.asset.infra.client;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Defect #2 fix verification: UserServiceClient.addOwnerRole is annotated @Retryable
 * (maxAttempts=5) with an @Recover fallback. This boots a tiny @EnableRetry context containing
 * the real (Spring-proxied) client and a mocked RestTemplate so the retry/recover wiring is
 * exercised for real, without the full asset-service context or a database.
 *
 * Note: the @Backoff (delay=500, multiplier=2.0) on the production method is a literal annotation
 * value and is honored at runtime, so the failing case sleeps ~7.5s total across the 4 backoffs.
 * That is the actual production behaviour; only the retry count + recover are asserted here.
 */
@SpringBootTest(classes = UserServiceClientRetryTest.RetryTestConfig.class)
@TestPropertySource(properties = {
        "user-service.base-url=http://localhost:0",
        "user-service.s2s-token=test-token"
})
class UserServiceClientRetryTest {

    @Configuration
    @EnableRetry
    static class RetryTestConfig {
        @Bean
        UserServiceClient userServiceClient(RestTemplate restTemplate) {
            return new UserServiceClient(restTemplate, "http://localhost:0", "test-token");
        }
    }

    @Autowired
    UserServiceClient client;

    @MockBean
    RestTemplate restTemplate;

    @Test
    void addOwnerRole_retriesFiveTimesThenRecoversWithoutThrowing() {
        when(restTemplate.postForEntity(anyString(), any(), eq(Void.class)))
                .thenThrow(new RestClientException("user-service unavailable"));

        // After 5 failed attempts the @Recover method handles it: no exception escapes.
        assertThatCode(() -> client.addOwnerRole(42L)).doesNotThrowAnyException();

        // Exactly maxAttempts=5 invocations of the underlying HTTP call.
        verify(restTemplate, times(5)).postForEntity(anyString(), any(), eq(Void.class));
    }

    @Test
    void addOwnerRole_succeedsFirstTry_noRetry() {
        when(restTemplate.postForEntity(anyString(), any(), eq(Void.class)))
                .thenReturn(org.springframework.http.ResponseEntity.ok().build());

        client.addOwnerRole(7L);

        verify(restTemplate, times(1)).postForEntity(anyString(), any(), eq(Void.class));
        Mockito.verifyNoMoreInteractions(restTemplate);
    }
}
