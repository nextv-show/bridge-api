package com.sanshuiyuan.matching.request.application;

import com.sanshuiyuan.matching.assignment.infra.MatchingAssignmentRepository;
import com.sanshuiyuan.matching.request.domain.DeviceStage;
import com.sanshuiyuan.matching.request.domain.MatchingRequest;
import com.sanshuiyuan.matching.request.domain.RequestStatus;
import com.sanshuiyuan.matching.request.infra.DeviceAssetGateway;
import com.sanshuiyuan.matching.request.infra.MatchingRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * P1-2：接单确认 SLA 任务（design §4）。每分钟扫一次 LOCKED 且未确认（claim_confirmed_at IS NULL）
 * 且 locked_at 已过软提醒节点的候选，按已过时长决策：
 * <ul>
 *   <li>≥ SLA(24h) → 自动释放回 {@code OPEN}（释放占用/回退设备/取消未发货物流/清 claim_confirmed_at）；</li>
 *   <li>跨 T+22h 窗口 → 最终预警（{@link ClaimConfirmNotifier.Stage#FINAL}）；</li>
 *   <li>跨 T+12h 窗口 → 软提醒（{@link ClaimConfirmNotifier.Stage#SOFT}）。</li>
 * </ul>
 * 提醒以「窗口」判定（无已发标记列）：每节点约触发 1 次，008 通道上线前为日志降级、可容忍偶发重发；
 * 上线后由 008 侧做幂等已发标记。已发货（物流非 PENDING_SHIP）的需求不释放（防取数后发货竞态）。
 * 与 7 天 {@link ExpireLockedRequestsJob}（→ EXPIRED）正交：此处针对「接单后迟迟不确认」、回 OPEN 重入池。
 */
@Component
public class ClaimConfirmSlaJob {

    private static final Logger log = LoggerFactory.getLogger(ClaimConfirmSlaJob.class);

    private static final String LOGISTICS_PENDING_SHIP = "PENDING_SHIP";
    /** 提醒判定窗口：略宽于扫描间隔以容忍调度抖动（代价是偶发重发，对软提醒无害）。 */
    static final long REMINDER_WINDOW_MINUTES = 2;

    enum Action { NONE, REMIND_SOFT, REMIND_FINAL, RELEASE }

    private final MatchingRequestRepository requestRepository;
    private final MatchingAssignmentRepository assignmentRepository;
    private final DeviceAssetGateway deviceAssetGateway;
    private final MatchingConfigService configService;
    private final ClaimConfirmNotifier notifier;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate txTemplate;

    public ClaimConfirmSlaJob(MatchingRequestRepository requestRepository,
                              MatchingAssignmentRepository assignmentRepository,
                              DeviceAssetGateway deviceAssetGateway,
                              MatchingConfigService configService,
                              ClaimConfirmNotifier notifier,
                              JdbcTemplate jdbcTemplate,
                              PlatformTransactionManager transactionManager) {
        this.requestRepository = requestRepository;
        this.assignmentRepository = assignmentRepository;
        this.deviceAssetGateway = deviceAssetGateway;
        this.configService = configService;
        this.notifier = notifier;
        this.jdbcTemplate = jdbcTemplate;
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelay = 60_000)
    public void scan() {
        int remind1 = configService.claimConfirmRemind1Hours();
        int remind2 = configService.claimConfirmRemind2Hours();
        int sla = configService.claimConfirmSlaHours();
        LocalDateTime now = LocalDateTime.now();
        // 只取已过第一个提醒节点的未确认锁定单（缩小候选集）。
        LocalDateTime since = now.minusHours(remind1);
        List<MatchingRequest> candidates = requestRepository
                .findByStatusAndClaimConfirmedAtIsNullAndLockedAtBefore(RequestStatus.LOCKED, since);

        for (MatchingRequest c : candidates) {
            if (c.getLockedAt() == null) continue;
            long elapsedMin = Duration.between(c.getLockedAt(), now).toMinutes();
            Action action = decide(elapsedMin, remind1, remind2, sla, REMINDER_WINDOW_MINUTES);
            long requestId = c.getId();
            switch (action) {
                case RELEASE -> {
                    try {
                        txTemplate.executeWithoutResult(s -> releaseOne(requestId));
                    } catch (Exception e) {
                        log.warn("ClaimSla: request_id={} 自动释放失败：{}", requestId, e.getMessage());
                    }
                }
                case REMIND_FINAL -> notifier.remind(requestId, c.getLockedByUserId(), ClaimConfirmNotifier.Stage.FINAL);
                case REMIND_SOFT -> notifier.remind(requestId, c.getLockedByUserId(), ClaimConfirmNotifier.Stage.SOFT);
                case NONE -> { /* 介于两节点之间，无动作 */ }
            }
        }
    }

    /** 纯决策（便于单测）：按已过分钟数判定提醒/释放。RELEASE 优先级最高。 */
    static Action decide(long elapsedMin, int remind1H, int remind2H, int slaH, long windowMin) {
        long sla = slaH * 60L;
        long r1 = remind1H * 60L;
        long r2 = remind2H * 60L;
        if (elapsedMin >= sla) return Action.RELEASE;
        if (elapsedMin >= r2 && elapsedMin < r2 + windowMin) return Action.REMIND_FINAL;
        if (elapsedMin >= r1 && elapsedMin < r1 + windowMin) return Action.REMIND_SOFT;
        return Action.NONE;
    }

    /** 单需求自动释放回 OPEN（在 TransactionTemplate 事务内）。已发货则跳过。 */
    private void releaseOne(long requestId) {
        MatchingRequest request = requestRepository.findById(requestId).orElse(null);
        if (request == null
                || request.getStatus() != RequestStatus.LOCKED
                || request.getClaimConfirmedAt() != null) {
            return;   // 期间已确认/状态已变 → 放弃释放
        }

        String logisticsStatus = findLogisticsStatus(requestId);
        if (logisticsStatus != null && !LOGISTICS_PENDING_SHIP.equals(logisticsStatus)) {
            return;
        }

        assignmentRepository.findByRequestIdAndReleasedAtIsNull(requestId).ifPresent(assignment -> {
            assignment.setReleasedAt(LocalDateTime.now());
            assignmentRepository.saveAndFlush(assignment);

            int reverted = deviceAssetGateway.advanceStage(
                    assignment.getDeviceAssetId(), assignment.getOwnerUserId(),
                    DeviceStage.LOCKED, DeviceStage.PENDING_MATCH);
            if (reverted != 1) {
                log.warn("ClaimSla: device_asset_id={} 回退 LOCKED→PENDING_MATCH 命中 {} 行",
                        assignment.getDeviceAssetId(), reverted);
            }
        });

        jdbcTemplate.update(
                "UPDATE logistics_orders SET status = 'CANCELLED' WHERE request_id = ? AND status = 'PENDING_SHIP'",
                requestId);

        request.setStatus(RequestStatus.OPEN);
        request.setLockedByUserId(null);
        request.setLockedAt(null);
        request.setClaimConfirmedAt(null);
        requestRepository.saveAndFlush(request);
        log.info("ClaimSla: request_id={} 未在 SLA 内确认 → LOCKED 释放回 OPEN", requestId);
    }

    private String findLogisticsStatus(long requestId) {
        List<String> rows = jdbcTemplate.queryForList(
                "SELECT status FROM logistics_orders WHERE request_id = ?", String.class, requestId);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
