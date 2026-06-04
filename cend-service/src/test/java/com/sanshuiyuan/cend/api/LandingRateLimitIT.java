package com.sanshuiyuan.cend.api;

import com.sanshuiyuan.cend.AbstractMysqlContainerTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B.2.5：公开接口限频。bucket 缩到 2 使边界确定（第 3 次同 IP → 429 + RATE_LIMITED）。
 */
@Tag("integration")
@DisabledIfEnvironmentVariable(named = "CI_SKIP_IT", matches = "true")
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "rate-limit.landing.max-requests=2",
        "rate-limit.landing.per-minutes=1"
})
class LandingRateLimitIT extends AbstractMysqlContainerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void landingConfig_exceedingLimit_returns429() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/api/c/landing/config")).andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/c/landing/config"))
                .andExpect(status().is(429))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }
}
