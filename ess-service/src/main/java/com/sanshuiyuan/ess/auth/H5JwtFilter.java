package com.sanshuiyuan.ess.auth;

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
 * 解析 {@code Authorization: Bearer <H5 JWT>}（或 query param {@code token}），
 * 校验通过后把 openid（统一身份）作为 principal 放入 SecurityContext。
 * <p>
 * <b>不拦截 / 不拒绝</b>：token 缺失或非法时直接放行，由 controller 层的
 * {@link CurrentOpenid#require()} / {@link ContractOwnershipGuard} 决定是否拒绝。
 * 这样线上若个别 H5 请求没带 token，错误会落在「业务层 401/403」而非「过滤器静默」，便于定位。
 * <p>
 * 仅对 {@code /api/h5/} 前缀生效。
 */
public class H5JwtFilter extends OncePerRequestFilter {

    private final H5JwtService jwtService;

    public H5JwtFilter(H5JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/h5/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
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
                var auth = new UsernamePasswordAuthenticationToken(
                        principal.canonicalId(), null, AuthorityUtils.NO_AUTHORITIES);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        filterChain.doFilter(request, response);
    }
}
