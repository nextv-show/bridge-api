package com.sanshuiyuan.matching.request.infra;

import com.sanshuiyuan.matching.request.domain.MatchingRequest;
import com.sanshuiyuan.matching.request.domain.RequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface MatchingRequestRepository extends JpaRepository<MatchingRequest, Long> {

    /**
     * B.2.5 防刷：同手机号 24h 内 status=OPEN 的 id 列表，**加 FOR UPDATE 行/间隙锁**。
     * 在发布事务内调用：InnoDB REPEATABLE READ 下，命中复合索引 idx_phone_hash_status_created
     * (contact_phone_hash, status, created_at) 的范围谓词会对该区间加间隙锁，阻塞同手机号的并发幻读插入，
     * 使「≤3 条 OPEN」上限原子化（避免 SELECT-then-INSERT 竞态被并发突破）。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r.id FROM MatchingRequest r WHERE r.contactPhoneHash = :hash " +
            "AND r.status = :status AND r.createdAt >= :since")
    List<Long> lockOpenIdsByPhoneSince(@Param("hash") String hash,
                                       @Param("status") RequestStatus status,
                                       @Param("since") LocalDateTime since);

    List<MatchingRequest> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<MatchingRequest> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, RequestStatus status);

    /**
     * nearby bbox 候选：status=OPEN + lat/lng 包围盒，排除调用方自己。走 idx_status_lat 的 lat 范围。
     * Haversine 精算与 min_price_tier 过滤在内存做。
     */
    @Query("SELECT r FROM MatchingRequest r WHERE r.status = com.sanshuiyuan.matching.request.domain.RequestStatus.OPEN " +
            "AND r.lat BETWEEN :latMin AND :latMax AND r.lng BETWEEN :lngMin AND :lngMax " +
            "AND r.userId <> :excludeUserId")
    List<MatchingRequest> findOpenInBoundingBox(@Param("latMin") BigDecimal latMin,
                                                @Param("latMax") BigDecimal latMax,
                                                @Param("lngMin") BigDecimal lngMin,
                                                @Param("lngMax") BigDecimal lngMax,
                                                @Param("excludeUserId") Long excludeUserId);
}
