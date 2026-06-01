package com.sanshuiyuan.h5.auth;

import com.sanshuiyuan.h5.common.BizException;
import com.sanshuiyuan.h5.common.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * 从 SecurityContext 取当前登录用户的统一身份（canonicalId，由 {@link H5JwtFilter} 注入）。
 * 受保护接口经 SecurityConfig 保证已认证，正常不会抛异常；此处仍兜底防御。
 *
 * <p>会话额外携带 {@code pay_openid}（渠道 JSAPI 预支付 payer）与 {@code clientType}，
 * 由 Filter 写入请求属性，经 {@link #requirePayOpenid()} / {@link #clientType()} 读取。
 */
public final class CurrentOpenid {

    static final String ATTR_PAY_OPENID = "h5.payOpenid";
    static final String ATTR_CLIENT_TYPE = "h5.clientType";

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

    /** 当前会话 clientType（{@code H5}/{@code MINI}）；缺省按 {@code H5}。 */
    public static String clientType() {
        String ct = attr(ATTR_CLIENT_TYPE);
        return ct == null || ct.isBlank() ? "H5" : ct;
    }

    /** 当前会话是否小程序端。 */
    public static boolean isMini() {
        return "MINI".equals(clientType());
    }

    /**
     * 当前渠道 JSAPI 预支付 payer.openid（公众号/小程序 openid）。
     * 缺失（多为旧 token 未带该 claim）时回退统一身份 {@link #require()}，保持公众号既有行为。
     */
    public static String requirePayOpenid() {
        String payOpenid = attr(ATTR_PAY_OPENID);
        if (payOpenid == null || payOpenid.isBlank()) {
            // 小程序会话必须携带真实小程序 openid，否则预支付无法完成。
            if (isMini()) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "缺少小程序 openid，请重新登录");
            }
            return require();
        }
        return payOpenid;
    }

    private static String attr(String name) {
        RequestAttributes ra = RequestContextHolder.getRequestAttributes();
        if (ra == null) {
            return null;
        }
        Object v = ra.getAttribute(name, RequestAttributes.SCOPE_REQUEST);
        return v == null ? null : v.toString();
    }
}
