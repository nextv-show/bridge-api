package com.sanshuiyuan.matching.assignment.infra;

import com.sanshuiyuan.matching.assignment.domain.MatchingAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MatchingAssignmentRepository extends JpaRepository<MatchingAssignment, Long> {

    Optional<MatchingAssignment> findByRequestId(Long requestId);

    /** 某需求当前活跃（未释放）的占用记录；落 active-scoped 唯一键 uk_request_active。接单前置校验用，避免已释放历史行误判为已占。 */
    Optional<MatchingAssignment> findByRequestIdAndReleasedAtIsNull(Long requestId);

    /** 某设备当前活跃（未释放）的占用记录；落 active-scoped 唯一键 uk_device_active。 */
    Optional<MatchingAssignment> findByDeviceAssetIdAndReleasedAtIsNull(Long deviceAssetId);

    List<MatchingAssignment> findByOwnerUserIdAndReleasedAtIsNullOrderByLockedAtDesc(Long ownerUserId);

    long countByOwnerUserIdAndReleasedAtIsNull(Long ownerUserId);

    /** P1-2 每日配额：owner 自 since 起的 claim 次数（含已释放，防接单→释放→再接的批量扫单）。 */
    long countByOwnerUserIdAndLockedAtGreaterThanEqual(Long ownerUserId, LocalDateTime since);
}
