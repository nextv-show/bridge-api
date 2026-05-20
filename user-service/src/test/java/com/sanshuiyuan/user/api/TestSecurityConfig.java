package com.sanshuiyuan.user.api;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Permissive security chain for @WebMvcTest controller slices. Keeps the standard
 * SecurityContextHolderFilter so SecurityMockMvcRequestPostProcessors.authentication() seeds the
 * context for @AuthenticationPrincipal, while permitting all requests in place of the production
 * JWT/S2S filter chain (which @WebMvcTest does not import and which needs JwtIssuer wiring).
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
