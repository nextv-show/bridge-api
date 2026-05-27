package com.sanshuiyuan.ess.exception;

/**
 * 回调签名验证失败异常。
 */
public class EssCallbackVerificationException extends RuntimeException {

    public EssCallbackVerificationException(String message) {
        super("回调签名验证失败: " + message);
    }

    public EssCallbackVerificationException(String message, Throwable cause) {
        super("回调签名验证失败: " + message, cause);
    }
}
