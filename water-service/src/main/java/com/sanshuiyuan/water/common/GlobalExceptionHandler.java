package com.sanshuiyuan.water.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 统一异常映射（钱包/充值公共层，@RestControllerAdvice）。
 * 失败统一包成 {@code {"code":<httpStatus>,"message":<msg>}}，状态码取自 {@link ErrorCode}。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Map<String, Object>> handleBiz(BizException ex) {
        ErrorCode code = ex.errorCode();
        if (code == ErrorCode.INTERNAL_ERROR) {
            log.error("业务异常(500)", ex);
        } else {
            log.warn("业务异常 code={} msg={}", code.code(), ex.getMessage());
        }
        return ResponseEntity.status(code.httpStatus())
                .body(ApiResponse.error(code, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse(ErrorCode.VALIDATION_FAILED.defaultMessage());
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.httpStatus())
                .body(ApiResponse.error(ErrorCode.VALIDATION_FAILED, message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("未处理异常", ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.httpStatus())
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage()));
    }
}
