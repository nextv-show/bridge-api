package com.sanshuiyuan.asset.api;

import com.sanshuiyuan.asset.application.NotOrderOwnerException;
import com.sanshuiyuan.asset.application.SkuUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * C.2.6: Centralised error mapping. Returns a uniform error body
 * {@code {"error": "...", "message": "..."}} with status codes aligned to the API contract:
 * <ul>
 *   <li>{@link MethodArgumentNotValidException} (bean validation, e.g. missing address) -> 422</li>
 *   <li>{@link SkuUnavailableException} (invalid/inactive SKU) -> 409</li>
 *   <li>{@link NotOrderOwnerException} (accessing another user's order) -> 403</li>
 *   <li>any other {@link Exception} -> 500 (details logged, not leaked to the response body)</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static Map<String, String> body(String error, String message) {
        return Map.of("error", error, "message", message == null ? "" : message);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse("请求参数校验失败");
        return ResponseEntity.unprocessableEntity().body(body("VALIDATION_FAILED", message));
    }

    @ExceptionHandler(SkuUnavailableException.class)
    public ResponseEntity<Map<String, String>> handleSkuUnavailable(SkuUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body("SKU_UNAVAILABLE", ex.getMessage()));
    }

    @ExceptionHandler(NotOrderOwnerException.class)
    public ResponseEntity<Map<String, String>> handleNotOwner(NotOrderOwnerException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body("FORBIDDEN", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body("INTERNAL_ERROR", "服务器内部错误"));
    }
}
