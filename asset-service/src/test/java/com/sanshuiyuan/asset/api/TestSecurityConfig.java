package com.sanshuiyuan.asset.api;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Permissive security chain for @WebMvcTest controller slices. It keeps the standard
 * SecurityContextHolderFilter in place (so SecurityMockMvcRequestPostProcessors.authentication()
 * populates the context for @AuthenticationPrincipal) while permitting all requests, replacing
 * the production JWT filter chain which is not imported by @WebMvcTest.
 */
@TestConfiguration
public class TestSecurityConfig {

    @Bean
    SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
