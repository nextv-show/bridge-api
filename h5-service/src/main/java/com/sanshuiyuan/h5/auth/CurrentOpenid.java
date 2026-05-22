package com.sanshuiyuan.h5.auth;

import com.sanshuiyuan.h5.common.BizException;
import com.sanshuiyuan.h5.common.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 从 SecurityContext 取当前登录用户的微信 openid（由 {@link H5JwtFilter} 注入）。
 * 受保护接口经 SecurityConfig 保证已认证，正常不会抛异常；此处仍兜底防御。
 */
public final class CurrentOpenid {

    private CurrentOpenid() {}

    public static String require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        String openid = auth.getName();
        if (openid == null || openid.isBlank()) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return openid;
    }
}
