package com.sanshuiyuan.matching.request.application;

import com.sanshuiyuan.matching.identity.MatchingUserResolver;
import com.sanshuiyuan.matching.request.api.ApiException;
import com.sanshuiyuan.matching.request.api.dto.ConfirmResponse;
import com.sanshuiyuan.matching.request.domain.MatchingRequest;
import com.sanshuiyuan.matching.request.domain.RequestStatus;
import com.sanshuiyuan.matching.request.infra.MatchingRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * P1-2：锁定方确认推进接单（design §4）。
 * <p>仅锁定方（request.lockedByUserId）对 LOCKED 需求确认，置 {@code claim_confirmed_at}，
 * 之后不再被 24h SLA 自动释放。<b>幂等</b>：已确认则直接回当前时间戳。
 */
@Service
public class ConfirmClaimUseCase {

    private static final Logger log = LoggerFactory.getLogger(ConfirmClaimUseCase.class);

    private final MatchingRequestRepository requestRepository;
    private final MatchingUserResolver userResolver;
    private final MatchingMetrics metrics;

    public ConfirmClaimUseCase(MatchingRequestRepository requestRepository,
                               MatchingUserResolver userResolver,
                               MatchingMetrics metrics) {
        this.requestRepository = requestRepository;
        this.userResolver = userResolver;
        this.metrics = metrics;
    }

    @Transactional
    public ConfirmResponse confirm(String subject, long requestId) {
        MatchingRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("NOT_FOUND", "需求不存在"));

        // 仅锁定方可确认（无 users 行 → 必非锁定方 → 403）。
        long userId = userResolver.findUserId(subject)
                .orElseThrow(() -> ApiException.forbidden("NOT_LOCK_OWNER", "无权确认该需求"));
        if (request.getLockedByUserId() == null || userId != request.getLockedByUserId()) {
            throw ApiException.forbidden("NOT_LOCK_OWNER", "无权确认该需求");
        }
        if (request.getStatus() != RequestStatus.LOCKED) {
            throw ApiException.conflict("REQUEST_NOT_LOCKED", "需求未锁定，无法确认");
        }

        if (request.getClaimConfirmedAt() == null) {
            request.setClaimConfirmedAt(LocalDateTime.now());
            try {
                requestRepository.saveAndFlush(request);
            } catch (OptimisticLockingFailureException e) {
                // 加载后被并发 release/expire/fulfill 改写 → 统一 409，而非 500。
                metrics.confirmConflict();
                throw ApiException.conflict("CONFIRM_CONFLICT", "需求状态已变更，请刷新重试");
            }
            log.info("Confirm: request_id={} 已确认推进（脱离 SLA 自动释放）", requestId);
        }
        return new ConfirmResponse(requestId, request.getStatus().name(), request.getClaimConfirmedAt());
    }
}
