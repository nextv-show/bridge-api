package com.sanshuiyuan.logistics.api;

import org.springframework.http.HttpStatus;

/** 带业务 code 与 HTTP 状态的受控异常（复刻 matching 同名类）。 */
public class LogisticsApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public LogisticsApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }

    public static LogisticsApiException unprocessable(String code, String message) {
        return new LogisticsApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }

    public static LogisticsApiException forbidden(String code, String message) {
        return new LogisticsApiException(HttpStatus.FORBIDDEN, code, message);
    }

    public static LogisticsApiException conflict(String code, String message) {
        return new LogisticsApiException(HttpStatus.CONFLICT, code, message);
    }

    public static LogisticsApiException notFound(String code, String message) {
        return new LogisticsApiException(HttpStatus.NOT_FOUND, code, message);
    }

    public static LogisticsApiException unauthorized(String code, String message) {
        return new LogisticsApiException(HttpStatus.UNAUTHORIZED, code, message);
    }
}
