package com.sanshuiyuan.h5.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> {})
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/h5/landing/**",
                    "/api/h5/specs",
                    "/api/h5/pay/callback",
                    "/api/h5/pay/refund-callback",
                    "/api/h5/pay/simulate-callback",
                    "/api/h5/kyc/**",
                    "/api-docs/**",
                    "/actuator/**"
                ).permitAll()
                .anyRequest().authenticated()
            );
        return http.build();
    }
}
