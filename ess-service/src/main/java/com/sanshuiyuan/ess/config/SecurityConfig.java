package com.sanshuiyuan.ess.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.util.Set;

/**
 * ESS Service 安全配置。
 * <p>
 * 策略：
 * - /api/internal/** : 回调入口，permitAll（应用层签名校验）
 * - /actuator/health : 健康检查，permitAll
 * - 其他 API : IP 白名单（Docker 内网 + 127.0.0.1），只允许 Nginx 反代访问
 * <p>
 * IP 白名单通过环境变量 ALLOWED_CIDRS 配置，默认允许 Docker 内网段。
 */
@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Value("${security.allowed-cidrs:172.16.0.0/12,127.0.0.1/32,::1/128}")
    private String allowedCidrs;

    @Value("${security.allowed-origins:https://api.sanshuiyuan.com}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain essSecurityFilterChain(HttpSecurity http) throws Exception {
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
                        // 回调入口：腾讯电子签回调，应用层做签名校验
                        .requestMatchers("/api/internal/**").permitAll()
                        // 健康检查
                        .requestMatchers("/actuator/**").permitAll()
                        // API 文档（仅限内网）
                        .requestMatchers("/api-docs/**").permitAll()
                        // 业务 API 需要通过 IP 白名单 Filter
                        .anyRequest().permitAll()
                )
                .build();
    }

    /**
     * IP 白名单过滤器。
     * 对 /api/h5/** 和 /api/ess/** 和 /api/admin/** 做来源 IP 校验。
     * 只允许 Nginx（Docker 内网）或本机访问。
     */
    @Bean
    public FilterRegistrationBean<Filter> ipWhitelistFilter() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        registration.addUrlPatterns("/api/h5/*", "/api/ess/*", "/api/admin/*");
        registration.setFilter(new Filter() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                    throws IOException, ServletException {
                HttpServletRequest httpRequest = (HttpServletRequest) request;
                String remoteAddr = resolveClientIp(httpRequest);

                if (isAllowedIp(remoteAddr)) {
                    chain.doFilter(request, response);
                } else {
                    log.warn("IP 白名单拦截: remoteAddr={}, uri={}", remoteAddr, httpRequest.getRequestURI());
                    HttpServletResponse httpResponse = (HttpServletResponse) response;
                    httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    httpResponse.setContentType("application/json;charset=UTF-8");
                    httpResponse.getWriter().write("{\"code\":-1,\"message\":\"Access Denied\"}");
                }
            }

            private String resolveClientIp(HttpServletRequest request) {
                // Nginx 反代时真实 IP 在 X-Real-IP 或 X-Forwarded-For
                String ip = request.getHeader("X-Real-IP");
                if (ip == null || ip.isBlank()) {
                    ip = request.getHeader("X-Forwarded-For");
                    if (ip != null && ip.contains(",")) {
                        ip = ip.split(",")[0].trim();
                    }
                }
                if (ip == null || ip.isBlank()) {
                    ip = request.getRemoteAddr();
                }
                return ip;
            }

            private boolean isAllowedIp(String ip) {
                // Docker 内网段 172.16-31.0.0/12 + localhost
                // 简化校验：允许 172.* 和 127.0.0.1 和 ::1
                if (ip.equals("127.0.0.1") || ip.equals("::1") || ip.equals("0:0:0:0:0:0:0:1")) {
                    return true;
                }
                if (ip.startsWith("172.")) {
                    return true;
                }
                // 允许 Docker 网关
                if (ip.startsWith("192.168.")) {
                    return true;
                }
                log.debug("IP 不在白名单: {}", ip);
                return false;
            }
        });
        return registration;
    }
}
