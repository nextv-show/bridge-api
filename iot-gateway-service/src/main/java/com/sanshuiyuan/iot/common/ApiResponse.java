package com.sanshuiyuan.iot.common;

import java.util.HashMap;
import java.util.Map;

/**
 * 统一响应包装（契约：成功 {@code code=0}，失败 {@code code=httpStatus}）。
 * 与 water-service / cend-service C 端约定一致。
 */
public final class ApiResponse {

    private ApiResponse() {
    }

    /** 成功：{@code {"code":0,"data":<data>}}。 */
    public static Map<String, Object> ok(Object data) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", 0);
        body.put("data", data);
        return body;
    }

    /** 成功（无数据）：{@code {"code":0}}。 */
    public static Map<String, Object> ok() {
        Map<String, Object> body = new HashMap<>();
        body.put("code", 0);
        return body;
    }

    /** 失败：{@code {"code":<httpStatus>,"message":<msg>}}。 */
    public static Map<String, Object> error(ErrorCode errorCode, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", errorCode.httpStatus().value());
        body.put("message", message);
        return body;
    }
}
