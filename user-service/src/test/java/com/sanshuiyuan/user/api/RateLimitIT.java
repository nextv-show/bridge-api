package com.sanshuiyuan.user.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanshuiyuan.user.AbstractMysqlContainerTest;
import com.sanshuiyuan.user.api.dto.AuthResponse;
import com.sanshuiyuan.user.api.dto.UserInfo;
import com.sanshuiyuan.user.application.WxLoginUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies RateLimitConfig's per-IP token bucket on /auth/**: requests within the limit pass,
 * the request that exceeds it is rejected with HTTP 429 by the interceptor before the controller.
 *
 * Design / trade-off (see report): driving this through the full Spring context with the real
 * interceptor is the faithful way to prove the 429 path (the interceptor is a private inner class,
 * not unit-testable in isolation). To avoid hitting the real WeChat API on the under-limit
 * requests, WxLoginUseCase is replaced with a @MockBean, and the bucket size is shrunk to 2 via
 * test properties so the boundary is cheap and deterministic (3rd request -> 429). The 429 itself
 * is produced entirely by RateLimitInterceptor.preHandle, independent of the mocked downstream.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "rate-limit.login.max-requests=2",
        "rate-limit.login.per-minutes=1"
})
class RateLimitIT extends AbstractMysqlContainerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    WxLoginUseCase wxLoginUseCase;

    @Test
    void authEndpoint_exceedingLimit_returns429() throws Exception {
        UserInfo info = new UserInfo(1L, "n", null, "CONSUMER", List.of("CONSUMER"));
        when(wxLoginUseCase.loginMiniProgram(anyString()))
                .thenReturn(new AuthResponse("a", "r", info));

        String body = objectMapper.writeValueAsString(Map.of("jsCode", "code"));

        // First 2 requests are within the bucket (limit = 2) and reach the controller.
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/auth/wx/miniprogram")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk());
        }

        // 3rd request exhausts the bucket: the interceptor short-circuits with 429.
        mockMvc.perform(post("/auth/wx/miniprogram")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(429))
                .andExpect(jsonPath("$.error").value("Too many requests"));
    }
}
