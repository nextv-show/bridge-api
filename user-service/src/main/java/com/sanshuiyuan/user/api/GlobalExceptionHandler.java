package com.sanshuiyuan.user.api;

import com.sanshuiyuan.user.infra.wx.WxAuthException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** 统一异常 → 干净 JSON 错误体 {code, message}。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 微信登录失败（如 40029 invalid code / 40163 code 已使用）→ 400 + 业务码。 */
    @ExceptionHandler(WxAuthException.class)
    public ResponseEntity<Map<String, String>> handleWxAuth(WxAuthException e) {
        log.warn("微信登录失败 code={} msg={}", e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("code", e.getCode(), "message", "微信登录失败，请重试"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArg(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("code", "BAD_REQUEST", "message", e.getMessage() != null ? e.getMessage() : "参数错误"));
    }
}
