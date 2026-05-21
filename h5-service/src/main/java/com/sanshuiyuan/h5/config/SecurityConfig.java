package com.sanshuiyuan.h5.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * h5-service 安全配置。
 * 本期（102）仅承载公开只读营销页接口，无登录态（决策门 G-8：首页不接微信授权）。
 * 因此全部放行；防刷由 {@link RateLimitConfig}（bucket4j 限频）兜底。
 * 103 引入下单/支付/授权后，可在此追加 JWT 过滤链与受保护路径。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> {})
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
