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

import java.time.LocalDateTime;
import java.util.List;

/**
 * FR-5：锁定超时回滚定时任务。
 * <p>每分钟扫描一次：status=LOCKED 且 locked_at 早于 {lock.ttl.days} 天，且物流仍 PENDING_SHIP 的需求，
 * 逐个回滚——需求置 EXPIRED、清空锁定信息、释放占用、回退设备阶段、取消物流单。
 * 每个需求独立事务回滚，单个失败不影响其余。已发货（物流非 PENDING_SHIP）的需求不回滚。
 */
@Component
public class ExpireLockedRequestsJob {

    private static final Logger log = LoggerFactory.getLogger(ExpireLockedRequestsJob.class);

    private static final String LOGISTICS_PENDING_SHIP = "PENDING_SHIP";

    private final MatchingRequestRepository requestRepository;
    private final MatchingAssignmentRepository assignmentRepository;
    private final DeviceAssetGateway deviceAssetGateway;
    private final MatchingConfigService configService;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate txTemplate;

    public ExpireLockedRequestsJob(MatchingRequestRepository requestRepository,
                                   MatchingAssignmentRepository assignmentRepository,
                                   DeviceAssetGateway deviceAssetGateway,
                                   MatchingConfigService configService,
                                   JdbcTemplate jdbcTemplate,
                                   PlatformTransactionManager transactionManager) {
        this.requestRepository = requestRepository;
        this.assignmentRepository = assignmentRepository;
        this.deviceAssetGateway = deviceAssetGateway;
        this.configService = configService;
        this.jdbcTemplate = jdbcTemplate;
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelay = 60_000)
    public void scan() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(configService.lockTtlDays());
        List<MatchingRequest> candidates =
                requestRepository.findByStatusAndLockedAtBefore(RequestStatus.LOCKED, threshold);
        for (MatchingRequest candidate : candidates) {
            long requestId = candidate.getId();
            try {
                // 每个需求独立事务回滚（TransactionTemplate 经事务管理器，避免自调用导致 @Transactional 失效）。
                txTemplate.executeWithoutResult(status -> expireOne(requestId));
            } catch (Exception e) {
                // 单个失败回滚自身事务，不影响其余候选。
                log.warn("ExpireLocked: request_id={} 回滚失败：{}", requestId, e.getMessage());
            }
        }
    }

    /** 单需求回滚（在 TransactionTemplate 提供的事务内执行）。物流已发货则跳过（防取数后发货竞态）。 */
    private void expireOne(long requestId) {
        MatchingRequest request = requestRepository.findById(requestId).orElse(null);
        if (request == null || request.getStatus() != RequestStatus.LOCKED) {
            return;
        }

        String logisticsStatus = findLogisticsStatus(requestId);
        if (logisticsStatus != null && !LOGISTICS_PENDING_SHIP.equals(logisticsStatus)) {
            return;
        }

        Long ownerUserId = request.getLockedByUserId();
        assignmentRepository.findByRequestIdAndReleasedAtIsNull(requestId).ifPresent(assignment -> {
            assignment.setReleasedAt(LocalDateTime.now());
            assignmentRepository.saveAndFlush(assignment);

            int reverted = deviceAssetGateway.advanceStage(
                    assignment.getDeviceAssetId(), assignment.getOwnerUserId(),
                    DeviceStage.LOCKED, DeviceStage.PENDING_MATCH);
            if (reverted != 1) {
                log.warn("ExpireLocked: device_asset_id={} 回退 LOCKED→PENDING_MATCH 命中 {} 行",
                        assignment.getDeviceAssetId(), reverted);
            }
        });

        jdbcTemplate.update(
                "UPDATE logistics_orders SET status = 'CANCELLED' WHERE request_id = ? AND status = 'PENDING_SHIP'",
                requestId);

        request.setStatus(RequestStatus.EXPIRED);
        request.setLockedByUserId(null);
        request.setLockedAt(null);
        requestRepository.saveAndFlush(request);
        log.info("ExpireLocked: request_id={} LOCKED → EXPIRED（锁定超时回滚）", requestId);

        onExpired(requestId, request.getUserId(), ownerUserId);
    }

    /** 双方通知钩子（008 接管）：超时回滚后通知需求方与原锁定方。 */
    private void onExpired(long requestId, Long demandUserId, Long ownerUserId) {
        // intentionally empty — 008 通知接管
    }

    private String findLogisticsStatus(long requestId) {
        List<String> rows = jdbcTemplate.queryForList(
                "SELECT status FROM logistics_orders WHERE request_id = ?", String.class, requestId);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
