package com.sanshuiyuan.matching.request.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanshuiyuan.matching.assignment.domain.MatchingAssignment;
import com.sanshuiyuan.matching.assignment.infra.MatchingAssignmentRepository;
import com.sanshuiyuan.matching.identity.MatchingUserResolver;
import com.sanshuiyuan.matching.logistics.domain.LogisticsOutboxEntry;
import com.sanshuiyuan.matching.logistics.infra.LogisticsOutboxRepository;
import com.sanshuiyuan.matching.request.api.ApiException;
import com.sanshuiyuan.matching.request.api.dto.ClaimRequestResponse;
import com.sanshuiyuan.matching.request.domain.DeviceStage;
import com.sanshuiyuan.matching.request.domain.MatchingRequest;
import com.sanshuiyuan.matching.request.domain.RequestStatus;
import com.sanshuiyuan.matching.request.infra.DeviceAssetGateway;
import com.sanshuiyuan.matching.request.infra.MatchingRequestRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** FR-3：接单原子事务。 */
@Service
public class ClaimRequestUseCase {

    private final MatchingRequestRepository requestRepository;
    private final MatchingAssignmentRepository assignmentRepository;
    private final LogisticsOutboxRepository outboxRepository;
    private final MatchingUserResolver userResolver;
    private final DeviceAssetGateway deviceAssetGateway;
    private final MatchingConfigService configService;
    private final ClaimRateLimiter rateLimiter;
    private final MatchingMetrics metrics;
    private final ObjectMapper objectMapper;

    public ClaimRequestUseCase(MatchingRequestRepository requestRepository,
                               MatchingAssignmentRepository assignmentRepository,
                               LogisticsOutboxRepository outboxRepository,
                               MatchingUserResolver userResolver,
                               DeviceAssetGateway deviceAssetGateway,
                               MatchingConfigService configService,
                               ClaimRateLimiter rateLimiter,
                               MatchingMetrics metrics,
                               ObjectMapper objectMapper) {
        this.requestRepository = requestRepository;
        this.assignmentRepository = assignmentRepository;
        this.outboxRepository = outboxRepository;
        this.userResolver = userResolver;
        this.deviceAssetGateway = deviceAssetGateway;
        this.configService = configService;
        this.rateLimiter = rateLimiter;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
    }

    /**
     * FR-3 接单原子事务：四步同事务落库，任一步失败整体回滚（无脏 outbox）。
     * <ol>
     *   <li>device_assets.stage：PENDING_MATCH → LOCKED（受限网关 CAS，归属+前置态原子校验）；</li>
     *   <li>matching_requests.status：OPEN → LOCKED（@Version 乐观锁，并发竞争败者抛 409）；</li>
     *   <li>matching_assignments：写活跃占用（uk_request_active / uk_device_active 兜底）；</li>
     *   <li>logistics_outbox：写跨服务工单（含 ship_to_address 快照）。</li>
     * </ol>
     * 先做廉价前置校验（404/403/409）快速短路；并发安全最终由步骤 1 的设备 CAS、步骤 2 的乐观锁、
     * 步骤 3 的活跃唯一键三道闸共同保证——前置校验仅为快路径与友好错误码。
     */
    @Transactional
    public ClaimRequestResponse claim(String subject, long requestId, long deviceAssetId) {
        if (!rateLimiter.tryConsume(requestId)) {
            metrics.claimRateLimited();
            throw ApiException.tooManyRequests("CLAIM_RATE_LIMITED", "接单过于频繁，请稍后重试");
        }

        long ownerUserId = userResolver.resolveUserId(subject);
        MatchingRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("NOT_FOUND", "需求不存在"));

        // —— 前置校验（快路径，最终一致由下方三道并发闸保证）——
        if (request.getStatus() != RequestStatus.OPEN || request.getLockedByUserId() != null) {
            throw ApiException.conflict("REQUEST_NOT_OPEN", "需求已被处理或接单");
        }
        if (!deviceAssetGateway.existsOwnedByUser(deviceAssetId, ownerUserId)) {
            throw ApiException.forbidden("NOT_OWNER_ASSET", "设备不属于当前用户");
        }
        if (assignmentRepository.findByRequestIdAndReleasedAtIsNull(requestId).isPresent()) {
            throw ApiException.conflict("REQUEST_ALREADY_CLAIMED", "需求已被接单");
        }
        if (assignmentRepository.countByOwnerUserIdAndReleasedAtIsNull(ownerUserId)
                >= configService.lockMaxPerOwner()) {
            throw ApiException.conflict("LOCK_LIMIT", "已达接单上限");
        }
        // P1-2 每日 claim 配额（含已释放，防接单→释放→再接的批量扫单）。
        // 注：与上方 lock.max.per.owner 同为「count-then-act」软限，并发下可轻微超额（无 DB 约束兜底）；
        // 硬完整性由设备 CAS + uk_device_active 保证（一设备一活跃占用）。如需严格原子，
        // 应对两处软限统一加 per-owner 串行化（users 行 FOR UPDATE），属后续硬化项，不在 P1 局部 bolt-on。
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        if (assignmentRepository.countByOwnerUserIdAndLockedAtGreaterThanEqual(ownerUserId, dayStart)
                >= configService.claimDailyQuotaPerOwner()) {
            metrics.claimQuotaExceeded();
            throw ApiException.tooManyRequests("CLAIM_QUOTA_EXCEEDED", "今日接单已达上限");
        }

        // 1) 设备 CAS：PENDING_MATCH → LOCKED。命中 0 行＝前置态非 PENDING_MATCH（已被占/不可接）。
        int staged = deviceAssetGateway.advanceStage(
                deviceAssetId, ownerUserId, DeviceStage.PENDING_MATCH, DeviceStage.LOCKED);
        if (staged != 1) {
            throw ApiException.conflict("DEVICE_STAGE_INVALID", "设备状态不允许接单");
        }

        try {
            // 2) 需求乐观锁：OPEN → LOCKED。并发竞争中仅一者 version 命中，败者 saveAndFlush 抛乐观锁异常。
            request.setStatus(RequestStatus.LOCKED);
            request.setLockedByUserId(ownerUserId);
            request.setLockedAt(LocalDateTime.now());
            request.setClaimConfirmedAt(null);   // P1-2：新接单待确认（重置上一轮可能残留的确认时间）
            requestRepository.saveAndFlush(request);

            // 3) 活跃占用：uk_request_active / uk_device_active 为最终兜底。
            MatchingAssignment assignment = new MatchingAssignment();
            assignment.setRequestId(requestId);
            assignment.setDeviceAssetId(deviceAssetId);
            assignment.setOwnerUserId(ownerUserId);
            assignmentRepository.saveAndFlush(assignment);

            // 4) 跨服务 outbox（与上述同事务，失败则随事务回滚，绝不留脏 outbox）。
            LogisticsOutboxEntry outbox = new LogisticsOutboxEntry();
            outbox.setRequestId(requestId);
            outbox.setDeviceAssetId(deviceAssetId);
            outbox.setPayloadJson(buildPayload(request, deviceAssetId, ownerUserId));
            outboxRepository.saveAndFlush(outbox);
        } catch (OptimisticLockingFailureException | DataIntegrityViolationException e) {
            // 并发竞争败者：整笔事务回滚（含步骤 1 的设备 stage），对外统一 409。
            metrics.claimConflict();
            throw ApiException.conflict("CLAIM_CONFLICT", "需求已被他人接单");
        }
        metrics.claimSuccess();

        return new ClaimRequestResponse(requestId, RequestStatus.LOCKED.name(), true);
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
