package com.sanshuiyuan.ess.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 客户端类型识别拦截器。
 * <p>
 * 解析 X-Client-Type 请求头，将客户端类型（H5/MINI/APP）存入请求属性，
 * 供下游 Controller/Service 使用。
 * <p>
 * 有效值: H5, MINI, APP（不区分大小写），默认 H5。
 */
@Component
public class ClientTypeInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ClientTypeInterceptor.class);

    /** 请求属性键 */
    public static final String CLIENT_TYPE_ATTR = "clientType";

    /** 请求头名称 */
    public static final String CLIENT_TYPE_HEADER = "X-Client-Type";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                              Object handler) {
        String headerValue = request.getHeader(CLIENT_TYPE_HEADER);
        ClientType clientType = parseClientType(headerValue);
        request.setAttribute(CLIENT_TYPE_ATTR, clientType);

        log.debug("客户端类型识别 [uri={}, clientType={}, rawHeader={}]",
                request.getRequestURI(), clientType, headerValue);

        return true;
    }

    /**
     * 从请求属性中获取客户端类型。
     */
    public static ClientType resolve(HttpServletRequest request) {
        Object attr = request.getAttribute(CLIENT_TYPE_ATTR);
        if (attr instanceof ClientType ct) {
            return ct;
        }
        return ClientType.H5; // 默认 H5
    }

    /**
     * 从字符串解析客户端类型。
     */
    public static ClientType parseClientType(String value) {
        if (value == null || value.isBlank()) {
            return ClientType.H5;
        }
        return switch (value.trim().toUpperCase()) {
            case "MINI" -> ClientType.MINI;
            case "APP" -> ClientType.APP;
            default -> ClientType.H5;
        };
    }

    /**
     * 客户端类型枚举。
     */
    public enum ClientType {
        /** H5 移动端 */
        H5,
        /** 微信小程序 */
        MINI,
        /** Flutter App */
        APP
    }
}
