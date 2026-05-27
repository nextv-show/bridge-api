package com.sanshuiyuan.ess.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 腾讯电子签 API 调用审计日志实体。
 */
@Entity
@Table(name = "ess_api_logs")
public class EssApiLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "api_action", nullable = false, length = 64)
    private String apiAction;

    @Column(name = "request_params", columnDefinition = "TEXT")
    private String requestParams;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected EssApiLog() {
    }

    /**
     * 创建 API 日志记录。
     */
    public static EssApiLog record(String apiAction, String requestParams,
                                    String responseBody, Integer statusCode,
                                    Integer durationMs, String errorMessage) {
        EssApiLog log = new EssApiLog();
        log.apiAction = apiAction;
        log.requestParams = requestParams;
        log.responseBody = responseBody;
        log.statusCode = statusCode;
        log.durationMs = durationMs;
        log.errorMessage = errorMessage;
        return log;
    }

    public Long getId() { return id; }
    public String getApiAction() { return apiAction; }
    public String getRequestParams() { return requestParams; }
    public String getResponseBody() { return responseBody; }
    public Integer getStatusCode() { return statusCode; }
    public Integer getDurationMs() { return durationMs; }
    public String getErrorMessage() { return errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
