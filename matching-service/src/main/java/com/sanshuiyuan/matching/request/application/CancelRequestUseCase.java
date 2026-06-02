package com.sanshuiyuan.matching.request.application;

import com.sanshuiyuan.matching.assignment.domain.MatchingAssignment;
import com.sanshuiyuan.matching.assignment.infra.MatchingAssignmentRepository;
import com.sanshuiyuan.matching.identity.MatchingUserResolver;
import com.sanshuiyuan.matching.request.api.ApiException;
import com.sanshuiyuan.matching.request.api.dto.CancelResponse;
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
import java.util.Optional;

/**
 * FR-5：联系人取消需求。
 * <p>需求方（user_id == request.userId）可取消 OPEN，或 LOCKED 且物流仍 PENDING_SHIP 的需求。
 * 单事务内：需求置 CANCELLED；若已 LOCKED 还需释放占用、回退设备阶段、取消物流单。
 * SHIPPED 之后一律 409。
 */
@Service
public class CancelRequestUseCase {

    private static final Logger log = LoggerFactory.getLogger(CancelRequestUseCase.class);

    private static final String LOGISTICS_PENDING_SHIP = "PENDING_SHIP";

    private final MatchingRequestRepository requestRepository;
    private final MatchingAssignmentRepository assignmentRepository;
    private final DeviceAssetGateway deviceAssetGateway;
    private final MatchingUserResolver userResolver;
    private final JdbcTemplate jdbcTemplate;

    public CancelRequestUseCase(MatchingRequestRepository requestRepository,
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
    public CancelResponse cancel(String subject, long requestId) {
        MatchingRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("NOT_FOUND", "需求不存在"));

        // 仅联系人本人可取消（无 users 行 → 必非本人 → 403）。
        long userId = userResolver.findUserId(subject)
                .orElseThrow(() -> ApiException.forbidden("NOT_DEMAND_OWNER", "无权取消该需求"));
        if (request.getUserId() == null || userId != request.getUserId()) {
            throw ApiException.forbidden("NOT_DEMAND_OWNER", "无权取消该需求");
        }

        RequestStatus status = request.getStatus();
        if (status == RequestStatus.OPEN) {
            // 纯 OPEN：尚无占用/物流，直接置 CANCELLED。
            request.setStatus(RequestStatus.CANCELLED);
            requestRepository.saveAndFlush(request);
            log.info("Cancel: request_id={} OPEN → CANCELLED", requestId);
            return new CancelResponse(requestId, RequestStatus.CANCELLED.name());
        }

        if (status != RequestStatus.LOCKED) {
            // CANCELLED / FULFILLED / EXPIRED 均不可取消。
            throw ApiException.conflict("REQUEST_NOT_CANCELLABLE", "当前状态不可取消");
        }

        // LOCKED：物流已发货则禁止取消。
        String logisticsStatus = findLogisticsStatus(requestId);
        if (logisticsStatus != null && !LOGISTICS_PENDING_SHIP.equals(logisticsStatus)) {
            throw ApiException.conflict("ALREADY_SHIPPED", "已发货无法取消，请联系客服");
        }

        rollbackLocked(request, requestId);
        request.setStatus(RequestStatus.CANCELLED);
        request.setLockedByUserId(null);
        request.setLockedAt(null);
        requestRepository.saveAndFlush(request);
        log.info("Cancel: request_id={} LOCKED → CANCELLED（已释放占用/回退设备/取消物流）", requestId);
        return new CancelResponse(requestId, RequestStatus.CANCELLED.name());
    }

    /** 释放活跃占用、回退设备阶段、取消物流单（与取消同事务）。 */
    private void rollbackLocked(MatchingRequest request, long requestId) {
        Optional<MatchingAssignment> active = assignmentRepository.findByRequestIdAndReleasedAtIsNull(requestId);
        if (active.isPresent()) {
            MatchingAssignment assignment = active.get();
            assignment.setReleasedAt(LocalDateTime.now());
            assignmentRepository.saveAndFlush(assignment);

            int reverted = deviceAssetGateway.advanceStage(
                    assignment.getDeviceAssetId(), assignment.getOwnerUserId(),
                    DeviceStage.LOCKED, DeviceStage.PENDING_MATCH);
            if (reverted != 1) {
                log.warn("Cancel: device_asset_id={} 回退 LOCKED→PENDING_MATCH 命中 {} 行（阶段或归属已变）",
                        assignment.getDeviceAssetId(), reverted);
            }
        }
        cancelLogistics(requestId);
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
