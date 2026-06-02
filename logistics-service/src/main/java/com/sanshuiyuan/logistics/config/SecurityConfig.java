package com.sanshuiyuan.logistics.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Phase A 脚手架：放行 actuator/webhook/api-docs。owner 视图与 ops/webhook 的真实鉴权
 * （H5 JWT / 白名单签名 / S2S token）在 Phase D 补。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(c -> c.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/actuator/**", "/api-docs/**", "/swagger-ui/**", "/logistics/webhook/**").permitAll()
                .anyRequest().permitAll());   // Phase A 脚手架：暂全放行，Phase D 收紧
        return http.build();
    }
}
