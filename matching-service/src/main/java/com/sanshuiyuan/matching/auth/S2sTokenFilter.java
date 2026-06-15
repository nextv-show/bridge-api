package com.sanshuiyuan.matching.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Service-to-service token 过滤器：Bearer S2S token 即授予认证。
 * 仅当请求尚未认证时生效（优先级低于 H5 JWT）。
 * 物流服务调用 /internal/matching/fulfill 时使用。
 */
public class S2sTokenFilter extends OncePerRequestFilter {
    private final String s2sToken;

    public S2sTokenFilter(String s2sToken) {
        this.s2sToken = s2sToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        String token = (header != null && header.startsWith("Bearer ")) ? header.substring(7) : null;
        if (token != null && token.equals(s2sToken)) {
            // ROLE_S2S：SecurityConfig 以 /internal/** hasRole("S2S") 门控，区别于 H5JwtFilter 授予的
            // 普通已认证用户（NO_AUTHORITIES）——后者不得访问 /internal/** 设备状态推进端点。
            var auth = new UsernamePasswordAuthenticationToken(
                    "s2s", null, AuthorityUtils.createAuthorityList("ROLE_S2S"));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        chain.doFilter(request, response);
    }
}
