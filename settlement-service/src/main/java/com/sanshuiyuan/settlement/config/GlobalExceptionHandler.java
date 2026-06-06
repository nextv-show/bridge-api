package com.sanshuiyuan.settlement.config;

import com.sanshuiyuan.settlement.application.guard.DailyLimitExceededException;
import com.sanshuiyuan.settlement.application.guard.KycNotVerifiedException;
import com.sanshuiyuan.settlement.application.guard.SingleLimitExceededException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** 统一异常 → 业务错误码映射（提现限额/KYC/参数校验）。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(KycNotVerifiedException.class)
    public ResponseEntity<Map<String, Object>> onKyc(KycNotVerifiedException e) {
        return ResponseEntity.status(403).body(Map.of("code", 403, "message", "KYC_NOT_VERIFIED"));
    }

    @ExceptionHandler(SingleLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> onSingleLimit(SingleLimitExceededException e) {
        return ResponseEntity.status(429).body(Map.of("code", 429, "message", "SINGLE_LIMIT_EXCEEDED"));
    }

    @ExceptionHandler(DailyLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> onDailyLimit(DailyLimitExceededException e) {
        return ResponseEntity.status(429).body(Map.of("code", 429, "message", "DAILY_LIMIT_EXCEEDED"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> onIllegalArg(IllegalArgumentException e) {
        return ResponseEntity.status(422).body(Map.of("code", 422, "message", e.getMessage()));
    }
}
