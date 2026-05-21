package com.sanshuiyuan.h5.common;

import java.util.UUID;

/**
 * 统一响应包装（脚手架公共层，103/104/105 复用）。
 * 成功：{@code code="OK"}；失败：{@code code} 取自 {@link ErrorCode}。
 *
 * @param code    业务码（"OK" 或错误码字符串）
 * @param message 提示文案
 * @param data    业务数据（失败时为 null）
 * @param traceId 链路追踪 id（便于日志/排障关联）
 */
public record ApiResponse<T>(String code, String message, T data, String traceId) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", "success", data, newTraceId());
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return new ApiResponse<>(errorCode.code(), errorCode.defaultMessage(), null, newTraceId());
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.code(), message, null, newTraceId());
    }

    private static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
