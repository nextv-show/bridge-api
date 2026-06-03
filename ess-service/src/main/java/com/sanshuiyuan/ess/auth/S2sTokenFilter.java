package com.sanshuiyuan.ess.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 服务间（S2S）鉴权过滤器：强制保护 {@code /api/admin/} 前缀。
 * <p>
 * ess-service 的 {@code /api/admin/contracts/**} 由 admin-service 的 BFF（{@code EssContractClient}）
 * 携带 {@code X-S2S-Token} 调用；管理后台用户身份由 admin-service 的 AdminJwtFilter 校验。
 * 此处校验 {@code X-S2S-Token} == {@code ${s2s-token}}，不符直接 401。
 */
public class S2sTokenFilter extends OncePerRequestFilter {

    private final String expectedToken;

    public S2sTokenFilter(String expectedToken) {
        this.expectedToken = expectedToken;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/admin/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = request.getHeader("X-S2S-Token");
        if (token == null || !token.equals(expectedToken)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
