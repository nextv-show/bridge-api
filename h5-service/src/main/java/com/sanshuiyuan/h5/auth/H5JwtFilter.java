package com.sanshuiyuan.h5.auth;

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
 * 解析 Authorization: Bearer &lt;H5 JWT&gt;，校验通过后把 openid 作为 principal 放入 SecurityContext。
 * 不做拦截/拒绝——是否要求登录由 SecurityConfig 的 authorizeHttpRequests 决定。
 */
public class H5JwtFilter extends OncePerRequestFilter {

    private final H5JwtService jwtService;

    public H5JwtFilter(H5JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 优先从 Authorization header 取，其次从 query param token（兼容 EventSource SSE）
        String token = null;
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            token = header.substring(7);
        } else {
            token = request.getParameter("token");
        }
        if (token != null) {
            H5JwtService.H5Principal principal = jwtService.parse(token);
            if (principal != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // principal name 仍为 canonicalId（统一身份），保证 CurrentOpenid.require() 行为不变。
                var auth = new UsernamePasswordAuthenticationToken(
                        principal.canonicalId(), null, AuthorityUtils.NO_AUTHORITIES);
                SecurityContextHolder.getContext().setAuthentication(auth);
                // 渠道支付 openid（预支付 payer）与 clientType（选支付通道）经请求属性下传。
                request.setAttribute(CurrentOpenid.ATTR_PAY_OPENID, principal.payOpenid());
                request.setAttribute(CurrentOpenid.ATTR_CLIENT_TYPE, principal.clientType());
            }
        }
        filterChain.doFilter(request, response);
    }
}
