package com.sanshuiyuan.ess.service;

import com.sanshuiyuan.ess.domain.EssApiLog;
import com.sanshuiyuan.ess.infra.repository.EssApiLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ESS API 调用日志记录服务。
 * <p>
 * 提供同步和异步日志记录，支持按动作/状态码/时间查询。
 */
@Service
public class EssApiLogService {

    private static final Logger log = LoggerFactory.getLogger(EssApiLogService.class);

    private final EssApiLogRepository apiLogRepository;

    public EssApiLogService(EssApiLogRepository apiLogRepository) {
        this.apiLogRepository = apiLogRepository;
    }

    /**
     * 同步记录 API 调用日志（成功）。
     */
    @Transactional
    public EssApiLog recordSuccess(String apiAction, String requestParams,
                                    String responseBody, Integer statusCode,
                                    Integer durationMs) {
        EssApiLog apiLog = EssApiLog.record(apiAction, requestParams, responseBody,
                statusCode, durationMs, null);
        EssApiLog saved = apiLogRepository.save(apiLog);
        log.debug("API 日志已记录 [action={}, status={}, duration={}ms]",
                apiAction, statusCode, durationMs);
        return saved;
    }

    /**
     * 同步记录 API 调用日志（失败）。
     */
    @Transactional
    public EssApiLog recordFailure(String apiAction, String requestParams,
                                    String responseBody, Integer statusCode,
                                    Integer durationMs, String errorMessage) {
        EssApiLog apiLog = EssApiLog.record(apiAction, requestParams, responseBody,
                statusCode, durationMs, errorMessage);
        EssApiLog saved = apiLogRepository.save(apiLog);
        log.warn("API 失败日志已记录 [action={}, status={}, error={}]",
                apiAction, statusCode, errorMessage);
        return saved;
    }

    /**
     * 异步记录 API 调用日志（成功），不阻塞调用方。
     */
    @Async
    @Transactional
    public void recordSuccessAsync(String apiAction, String requestParams,
                                    String responseBody, Integer statusCode,
                                    Integer durationMs) {
        recordSuccess(apiAction, requestParams, responseBody, statusCode, durationMs);
    }

    /**
     * 异步记录 API 调用日志（失败），不阻塞调用方。
     */
    @Async
    @Transactional
    public void recordFailureAsync(String apiAction, String requestParams,
                                    String responseBody, Integer statusCode,
                                    Integer durationMs, String errorMessage) {
        recordFailure(apiAction, requestParams, responseBody, statusCode, durationMs, errorMessage);
    }

    /**
     * 按动作和时间查询日志。
     */
    @Transactional(readOnly = true)
    public List<EssApiLog> findByActionAndTime(String apiAction, LocalDateTime after) {
        return apiLogRepository.findByApiActionAndCreatedAtAfter(apiAction, after);
    }

    /**
     * 按状态码和时间查询日志。
     */
    @Transactional(readOnly = true)
    public List<EssApiLog> findByStatusAndTime(Integer statusCode, LocalDateTime after) {
        return apiLogRepository.findByStatusCodeAndCreatedAtAfter(statusCode, after);
    }

    /**
     * 统计指定动作和时间后的调用次数。
     */
    @Transactional(readOnly = true)
    public long countByActionAndTime(String apiAction, LocalDateTime after) {
        return apiLogRepository.countByApiActionAndCreatedAtAfter(apiAction, after);
    }
}
