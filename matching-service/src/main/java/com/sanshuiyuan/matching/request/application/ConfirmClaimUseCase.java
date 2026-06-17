package com.sanshuiyuan.matching.request.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanshuiyuan.matching.assignment.domain.MatchingAssignment;
import com.sanshuiyuan.matching.assignment.infra.MatchingAssignmentRepository;
import com.sanshuiyuan.matching.identity.MatchingUserResolver;
import com.sanshuiyuan.matching.logistics.domain.LogisticsOutboxEntry;
import com.sanshuiyuan.matching.logistics.infra.LogisticsOutboxRepository;
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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * P1-2：锁定方确认推进接单（design §4）。
 * <p>仅锁定方（request.lockedByUserId）对 LOCKED 需求确认，置 {@code claim_confirmed_at}，
 * 之后不再被 24h SLA 自动释放。确认成功后写 logistics_outbox 触发发货（接单时不写，避免未确认即建/撤
 * 物流工单的空转）。<b>幂等</b>：已确认则直接回当前时间戳，不重复写 outbox。
 */
@Service
public class ConfirmClaimUseCase {

    private static final Logger log = LoggerFactory.getLogger(ConfirmClaimUseCase.class);

    private final MatchingRequestRepository requestRepository;
    private final MatchingAssignmentRepository assignmentRepository;
    private final LogisticsOutboxRepository outboxRepository;
    private final MatchingUserResolver userResolver;
    private final MatchingMetrics metrics;
    private final ObjectMapper objectMapper;

    public ConfirmClaimUseCase(MatchingRequestRepository requestRepository,
                               MatchingAssignmentRepository assignmentRepository,
                               LogisticsOutboxRepository outboxRepository,
                               MatchingUserResolver userResolver,
                               MatchingMetrics metrics,
                               ObjectMapper objectMapper) {
        this.requestRepository = requestRepository;
        this.assignmentRepository = assignmentRepository;
        this.outboxRepository = outboxRepository;
        this.userResolver = userResolver;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
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

            // 确认推进后才写跨服务物流工单（同事务，失败则随确认一并回滚）。
            MatchingAssignment assignment = assignmentRepository
                    .findByRequestIdAndReleasedAtIsNull(requestId)
                    .orElseThrow(() -> ApiException.conflict("NO_ASSIGNMENT", "无活跃接单记录"));
            long deviceAssetId = assignment.getDeviceAssetId();
            long ownerUserId = request.getLockedByUserId();

            LogisticsOutboxEntry outbox = new LogisticsOutboxEntry();
            outbox.setRequestId(requestId);
            outbox.setDeviceAssetId(deviceAssetId);
            outbox.setSource("MATCHING");
            outbox.setPayloadJson(buildPayload(request, deviceAssetId, ownerUserId));
            outboxRepository.saveAndFlush(outbox);
            log.info("Confirm: request_id={} 写 logistics_outbox device={} 触发发货", requestId, deviceAssetId);
        }
        return new ConfirmResponse(requestId, request.getStatus().name(),
                request.getClaimConfirmedAt(), true);
    }

    private String buildPayload(MatchingRequest request, long deviceAssetId, long ownerUserId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("request_id", request.getId());
        payload.put("device_asset_id", deviceAssetId);
        payload.put("owner_user_id", ownerUserId);
        payload.put("ship_to_address", request.getAddress());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("构造 outbox payload 失败", e);
        }
    }
}
