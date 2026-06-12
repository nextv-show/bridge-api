package com.sanshuiyuan.matching.request.application;

import com.sanshuiyuan.matching.assignment.domain.MatchingAssignment;
import com.sanshuiyuan.matching.assignment.infra.MatchingAssignmentRepository;
import com.sanshuiyuan.matching.identity.MatchingUserResolver;
import com.sanshuiyuan.matching.request.api.ApiException;
import com.sanshuiyuan.matching.request.api.dto.ReleaseResponse;
import com.sanshuiyuan.matching.request.domain.DeviceStage;
import com.sanshuiyuan.matching.request.domain.MatchingRequest;
import com.sanshuiyuan.matching.request.domain.RequestStatus;
import com.sanshuiyuan.matching.request.infra.DeviceAssetGateway;
import com.sanshuiyuan.matching.request.infra.MatchingRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * FR-5：锁定方释放需求。
 * <p>锁定方（request.lockedByUserId）对 LOCKED 且物流仍 PENDING_SHIP 的需求执行释放：
 * 需求回到 OPEN（可重新接单），清空锁定信息，释放占用、回退设备阶段、取消物流单。
 * SHIPPED 之后一律 409。
 */
@Service
public class ReleaseRequestUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReleaseRequestUseCase.class);

    private static final String LOGISTICS_PENDING_SHIP = "PENDING_SHIP";

    private final MatchingRequestRepository requestRepository;
    private final MatchingAssignmentRepository assignmentRepository;
    private final DeviceAssetGateway deviceAssetGateway;
    private final MatchingUserResolver userResolver;
    private final JdbcTemplate jdbcTemplate;

    public ReleaseRequestUseCase(MatchingRequestRepository requestRepository,
                                 MatchingAssignmentRepository assignmentRepository,
                                 DeviceAssetGateway deviceAssetGateway,
                                 MatchingUserResolver userResolver,
                                 JdbcTemplate jdbcTemplate) {
        this.requestRepository = requestRepository;
        this.assignmentRepository = assignmentRepository;
        this.deviceAssetGateway = deviceAssetGateway;
        this.userResolver = userResolver;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public ReleaseResponse release(String subject, long requestId) {
        MatchingRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("NOT_FOUND", "需求不存在"));

        // 仅锁定方可释放（无 users 行 → 必非锁定方 → 403）。
        long userId = userResolver.findUserId(subject)
                .orElseThrow(() -> ApiException.forbidden("NOT_LOCK_OWNER", "无权释放该需求"));
        if (request.getLockedByUserId() == null || userId != request.getLockedByUserId()) {
            throw ApiException.forbidden("NOT_LOCK_OWNER", "无权释放该需求");
        }
        if (request.getStatus() != RequestStatus.LOCKED) {
            throw ApiException.conflict("REQUEST_NOT_LOCKED", "需求未锁定，无法释放");
        }

        // 物流已发货则禁止释放。
        String logisticsStatus = findLogisticsStatus(requestId);
        if (logisticsStatus != null && !LOGISTICS_PENDING_SHIP.equals(logisticsStatus)) {
            throw ApiException.conflict("ALREADY_SHIPPED", "已发货无法释放，请联系客服");
        }

        // 释放活跃占用 + 回退设备阶段。
        assignmentRepository.findByRequestIdAndReleasedAtIsNull(requestId).ifPresent(assignment -> {
            assignment.setReleasedAt(LocalDateTime.now());
            assignmentRepository.saveAndFlush(assignment);

            int reverted = deviceAssetGateway.advanceStage(
                    assignment.getDeviceAssetId(), assignment.getOwnerUserId(),
                    DeviceStage.LOCKED, DeviceStage.PENDING_MATCH);
            if (reverted != 1) {
                log.warn("Release: device_asset_id={} 回退 LOCKED→PENDING_MATCH 命中 {} 行（阶段或归属已变）",
                        assignment.getDeviceAssetId(), reverted);
            }
        });

        cancelLogistics(requestId);

        // 需求回到 OPEN，清空锁定信息。
        request.setStatus(RequestStatus.OPEN);
        request.setLockedByUserId(null);
        request.setLockedAt(null);
        request.setClaimConfirmedAt(null);
        requestRepository.saveAndFlush(request);
        log.info("Release: request_id={} LOCKED → OPEN（已释放占用/回退设备/取消物流）", requestId);
        return new ReleaseResponse(requestId, RequestStatus.OPEN.name());
    }

    private String findLogisticsStatus(long requestId) {
        List<String> rows = jdbcTemplate.queryForList(
                "SELECT status FROM logistics_orders WHERE request_id = ?", String.class, requestId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void cancelLogistics(long requestId) {
        jdbcTemplate.update(
                "UPDATE logistics_orders SET status = 'CANCELLED' WHERE request_id = ? AND status = 'PENDING_SHIP'",
                requestId);
    }
}
