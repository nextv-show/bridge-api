package com.sanshuiyuan.h5.common;

import org.springframework.http.HttpStatus;

/**
 * h5-service 业务错误码（脚手架公共层）。每个码绑定 HTTP 状态 + 默认文案。
 */
public enum ErrorCode {

    NO_ACTIVE_CONFIG(HttpStatus.SERVICE_UNAVAILABLE, "NO_ACTIVE_CONFIG", "暂无生效的落地页配置"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "未登录或登录已失效"),
    WX_AUTH_FAILED(HttpStatus.BAD_GATEWAY, "WX_AUTH_FAILED", "微信授权失败"),
    KYC_INIT_FAILED(HttpStatus.BAD_GATEWAY, "KYC_INIT_FAILED", "实名认证初始化失败"),
    KYC_QUERY_FAILED(HttpStatus.BAD_GATEWAY, "KYC_QUERY_FAILED", "实名认证结果查询失败"),
    PAY_PREPAY_FAILED(HttpStatus.BAD_GATEWAY, "PAY_PREPAY_FAILED", "发起微信支付失败"),
    REFUND_FAILED(HttpStatus.BAD_GATEWAY, "REFUND_FAILED", "发起退款失败"),
    VALIDATION_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", "请求参数校验失败"),
    COMPLIANCE_VIOLATION(HttpStatus.UNPROCESSABLE_ENTITY, "COMPLIANCE_VIOLATION", "文案合规校验未通过"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "请求过于频繁，请稍后再试"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "服务器内部错误"),
    KYC_REQUIRED(HttpStatus.CONFLICT, "KYC_REQUIRED", "请先完成实名认证"),
    SPEC_NOT_FOUND(HttpStatus.NOT_FOUND, "SPEC_NOT_FOUND", "规格不存在"),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "订单不存在"),
    ORDER_STATUS_CONFLICT(HttpStatus.CONFLICT, "ORDER_STATUS_CONFLICT", "订单状态不允许此操作"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "无权操作此订单"),
    COOLDOWN_EXPIRED(HttpStatus.CONFLICT, "COOLDOWN_EXPIRED", "冷静期已结束，无法申请退款"),
    ORDER_NOT_REFUNDABLE(HttpStatus.CONFLICT, "ORDER_NOT_REFUNDABLE", "当前订单状态不允许退款"),
    KYC_ID_CARD_CONFLICT(HttpStatus.CONFLICT, "KYC_ID_CARD_CONFLICT", "该身份证已绑定其他账号，无法重复实名");

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
