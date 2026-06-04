package com.sanshuiyuan.ess.config;

import com.sanshuiyuan.ess.auth.H5JwtFilter;
import com.sanshuiyuan.ess.auth.H5JwtService;
import com.sanshuiyuan.ess.auth.S2sTokenFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * ESS Service 安全配置。
 * <p>
 * 鉴权策略（替代旧的「全 permitAll + 对 nginx 流量空操作的 IP 白名单」）：
 * <ul>
 *   <li>{@code /api/internal/**}（腾讯电子签回调，应用层验签）、{@code /actuator/**}、
 *       {@code /api-docs/**}：permitAll。</li>
 *   <li>{@code /api/admin/**}：由 {@link S2sTokenFilter} 强制校验 {@code X-S2S-Token}
 *       （admin-service BFF 携带）。</li>
 *   <li>{@code /api/c/**}：由 {@link H5JwtFilter} 解析 H5 JWT 注入 SecurityContext；
 *       <b>不在过滤器层拒绝</b>，由 controller 的 owner guard / CurrentOpenid.require 决定拒绝，
 *       便于线上定位「未带 token」与「非属主」两类失败。</li>
 *   <li>其余 permitAll（鉴权交给上述两个 filter，沿用项目「Spring Security 放行 + 自定义 filter」风格，
 *       避免冲突）。</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    @Value("${security.allowed-origins:https://api.sanshuiyuan.com}")
    private String allowedOrigins;

    @Value("${s2s-token:local-dev-static-token}")
    private String s2sToken;

    @Value("${h5.jwt-secret:dev-h5-jwt-secret-please-override-in-prod-0001}")
    private String h5JwtSecret;

    @Value("${h5.jwt-ttl-hours:72}")
    private int h5JwtTtlHours;

    @Bean
    public H5JwtService h5JwtService() {
        return new H5JwtService(h5JwtSecret, h5JwtTtlHours);
    }

    @Bean
    public SecurityFilterChain essSecurityFilterChain(HttpSecurity http, H5JwtService h5JwtService) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(request -> {
                    var config = new org.springframework.web.cors.CorsConfiguration();
                    config.setAllowedOrigins(java.util.List.of(allowedOrigins.split(",")));
                    config.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                    config.setAllowedHeaders(java.util.List.of("*"));
                    config.setMaxAge(3600L);
                    return config;
                }))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/internal/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/api-docs/**").permitAll()
                        // 鉴权由下面两个自定义 filter 承担，Spring Security 放行避免冲突。
                        .anyRequest().permitAll()
                )
                .addFilterBefore(new S2sTokenFilter(s2sToken), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new H5JwtFilter(h5JwtService), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
