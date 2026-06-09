package com.sanshuiyuan.ess.controller;

import com.sanshuiyuan.ess.exception.EssApiException;
import com.sanshuiyuan.ess.exception.EssCallbackVerificationException;
import com.sanshuiyuan.ess.exception.EssFlowException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * 签署链路异常统一处理。
 * <p>
 * 捕获 EssApiException / EssFlowException / EssCallbackVerificationException，
 * 返回结构化错误 JSON，避免泄漏内部堆栈。
 */
@RestControllerAdvice
public class EssExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(EssExceptionHandler.class);

    /**
     * API 调用异常 — 上游服务故障。
     */
    @ExceptionHandler(EssApiException.class)
    public ResponseEntity<Map<String, Object>> handleApiException(EssApiException ex) {
        log.error("ESS API 异常 [action={}]: {}", ex.getApiAction(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "code", -1,
                "error", "EssApiError",
                "message", ex.getMessage()
        ));
    }

    /**
     * 签署流程异常 — 业务逻辑错误。
     */
    @ExceptionHandler(EssFlowException.class)
    public ResponseEntity<Map<String, Object>> handleFlowException(EssFlowException ex) {
        log.error("签署流程异常 [contractId={}, flowId={}]: {}",
                ex.getContractId(), ex.getFlowId(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "code", -2,
                "error", "EssFlowError",
                "message", ex.getMessage()
        ));
    }

    /**
     * 回调签名验证失败 — 伪造请求。
     */
    @ExceptionHandler(EssCallbackVerificationException.class)
    public ResponseEntity<Map<String, Object>> handleCallbackVerificationException(
            EssCallbackVerificationException ex) {
        log.warn("回调签名验证失败: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "code", -3,
                "error", "CallbackVerificationError",
                "message", ex.getMessage()
        ));
    }

    /**
     * 显式状态码异常（鉴权/owner 校验等）——honor 其 HttpStatus，勿被兜底吞成 500。
     * spec 006 的 401（未登录）/ 403（非属主）经此返回。
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        log.warn("请求被拒绝 [status={}]: {}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of(
                "code", -1,
                "error", "RequestRejected",
                "message", ex.getReason() != null ? ex.getReason() : "请求被拒绝"
        ));
    }

    /**
     * 参数校验失败（缺参/格式错误）——属客户端错误，返回 400 而非 500。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("请求参数错误: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "code", -1,
                "error", "BadRequest",
                "message", ex.getMessage() != null ? ex.getMessage() : "请求参数错误"
        ));
    }

    /**
     * 兜底异常处理。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("未预期异常: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "code", -99,
                "error", "InternalServerError",
                "message", "服务内部错误，请稍后重试"
        ));
    }
}
