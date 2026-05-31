package com.sanshuiyuan.asset.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 入站 service-to-service 鉴权：仅对 {@code /internal/} 前缀的内部接口生效。
 *
 * <p>要求请求头 {@code X-S2S-Token} 等于本服务期望的共享密钥（{@code ${s2s-token}}），
 * 缺失或不符一律 401 并短路；非 {@code /internal/} 路径直接放行（交由后续 JWT 过滤器处理）。
 *
 * <p>镜像 user-service 的同名内部过滤器：两端共享同一 {@code S2S_TOKEN} 环境变量，
 * 默认值也保持字节级一致（{@code local-dev-static-token}），prod 未设环境变量时两端仍能对齐。
 *
 * <p>注意区分：本服务 {@code user-service.s2s-token} 是<b>出站</b>调用 user-service 时携带的令牌，
 * 而本过滤器校验的 {@code s2s-token} 是<b>入站</b>期望令牌，二者用途不同。
 */
public class S2sTokenFilter extends OncePerRequestFilter {

    private final String expectedToken;

    public S2sTokenFilter(String expectedToken) {
        this.expectedToken = expectedToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/internal/")) {
            String token = request.getHeader("X-S2S-Token");
            if (token == null || !token.equals(expectedToken)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
