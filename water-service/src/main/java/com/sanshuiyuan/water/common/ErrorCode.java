package com.sanshuiyuan.water.common;

import org.springframework.http.HttpStatus;

/**
 * water-service 业务错误码（钱包/充值）。每个码绑定 HTTP 状态 + 默认文案。
 */
public enum ErrorCode {

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "未登录或登录已失效"),
    VALIDATION_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", "请求参数校验失败"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "请求过于频繁，请稍后再试"),
    WALLET_NOT_FOUND(HttpStatus.NOT_FOUND, "WALLET_NOT_FOUND", "钱包不存在"),
    TOPUP_MIN_AMOUNT(HttpStatus.UNPROCESSABLE_ENTITY, "TOPUP_MIN_AMOUNT", "充值金额低于最低限额"),
    PAY_PREPAY_FAILED(HttpStatus.BAD_GATEWAY, "PAY_PREPAY_FAILED", "发起微信支付失败"),
    DEVICE_LOCKED(HttpStatus.FORBIDDEN, "DEVICE_LOCKED", "设备已锁定，无法出水"),
    DEVICE_IN_USE(HttpStatus.CONFLICT, "DEVICE_IN_USE", "设备正在使用中"),
    DEVICE_OFFLINE(HttpStatus.SERVICE_UNAVAILABLE, "DEVICE_OFFLINE", "设备离线"),
    LOW_BALANCE(HttpStatus.UNPROCESSABLE_ENTITY, "LOW_BALANCE", "余额不足"),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "取水会话不存在"),
    SESSION_NOT_ACTIVE(HttpStatus.CONFLICT, "SESSION_NOT_ACTIVE", "会话已结束"),
    BILL_NOT_FOUND(HttpStatus.NOT_FOUND, "BILL_NOT_FOUND", "账单不存在"),
    IOT_COMMAND_FAILED(HttpStatus.GATEWAY_TIMEOUT, "IOT_COMMAND_FAILED", "设备命令下发失败"),
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
