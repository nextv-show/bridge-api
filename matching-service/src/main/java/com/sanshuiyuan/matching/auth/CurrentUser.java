package com.sanshuiyuan.matching.auth;

import com.sanshuiyuan.matching.request.api.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** 从 SecurityContext 取当前登录主体（subject = openid/unionid）。 */
public final class CurrentUser {

    private CurrentUser() {}

    /** 返回 subject；未登录抛 401。 */
    public static String subject() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null || !(auth.getPrincipal() instanceof String s) || s.isBlank()) {
            throw ApiException.unauthorized("UNAUTHORIZED", "未登录");
        }
        return s;
    }
}
