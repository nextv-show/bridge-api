package com.sanshuiyuan.h5.common;

import org.springframework.http.HttpStatus;

/**
 * h5-service 业务错误码（脚手架公共层）。每个码绑定 HTTP 状态 + 默认文案。
 */
public enum ErrorCode {

    NO_ACTIVE_CONFIG(HttpStatus.SERVICE_UNAVAILABLE, "NO_ACTIVE_CONFIG", "暂无生效的落地页配置"),
    VALIDATION_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", "请求参数校验失败"),
    COMPLIANCE_VIOLATION(HttpStatus.UNPROCESSABLE_ENTITY, "COMPLIANCE_VIOLATION", "文案合规校验未通过"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "请求过于频繁，请稍后再试"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "服务器内部错误");

    private final HttpStatus httpStatus;
    private final String code;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String code, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
