package com.sanshuiyuan.ess.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

/**
 * 从 SecurityContext 取当前 H5 会话的统一身份（openid，由 {@link H5JwtFilter} 注入）。
 * <p>
 * ess-service 无 cend 的 BizException/ErrorCode 体系，未认证时抛
 * {@link ResponseStatusException}（401 UNAUTHORIZED）。
 */
public final class CurrentOpenid {

    private CurrentOpenid() {}

    public static String require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // anyRequest().permitAll() 下 Spring Security 会塞匿名身份；匿名视为未登录。
        if (auth == null || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken
                || auth.getPrincipal() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录或登录已过期");
        }
        String openid = auth.getName();
        if (openid == null || openid.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录或登录已过期");
        }
        return openid;
    }
}
