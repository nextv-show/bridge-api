package com.sanshuiyuan.matching.request.infra;

import com.sanshuiyuan.matching.request.domain.MatchingRequest;
import com.sanshuiyuan.matching.request.domain.PriceTier;
import com.sanshuiyuan.matching.request.domain.RequestStatus;
import com.sanshuiyuan.matching.request.domain.SceneType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
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

    /** FR-5 超时回滚扫描：指定状态且 locked_at 早于阈值的需求（锁定超时候选）。 */
    List<MatchingRequest> findByStatusAndLockedAtBefore(RequestStatus status, LocalDateTime lockedAtBefore);

    /** P1-2 SLA 扫描：LOCKED 且未确认（claim_confirmed_at IS NULL）且 locked_at 早于阈值的候选（提醒/自动释放）。 */
    List<MatchingRequest> findByStatusAndClaimConfirmedAtIsNullAndLockedAtBefore(
            RequestStatus status, LocalDateTime lockedAtBefore);

    /**
     * P1-1 nearby 第一层候选（design §5.2「两层架构」）：status=OPEN + lat/lng 包围盒，排除调用方自己，
     * 下推 scene_type（可空）与 expected_price_tier ∈ tiers（调用方据 min_price_tier 展开为允许档位集合，
     * 不过滤时传全 4 档）。走 idx_status_lat 的 lat 范围。
     *
     * <p>候选用 {@link Pageable} 截断到 nearby.candidate.limit，<b>ORDER BY 平面近似距离 ASC, id ASC</b>：
     * 高密度区 bbox 超过上限时优先保留<b>近端</b>候选——近的需求绝不因新旧被丢弃（贴合「附近」语义、
     * 不偏向 distance/revenue/latest 任一排序），id 兜底消除 created_at 并列导致的非确定截断。
     * 平面距离（不含 cos 修正）仅用于<b>截断粗排</b>；最终距离仍走应用层 Haversine 精算裁圆。
     */
    @Query("SELECT r FROM MatchingRequest r WHERE r.status = com.sanshuiyuan.matching.request.domain.RequestStatus.OPEN " +
            "AND r.lat BETWEEN :latMin AND :latMax AND r.lng BETWEEN :lngMin AND :lngMax " +
            "AND r.userId <> :excludeUserId " +
            "AND (:sceneType IS NULL OR r.sceneType = :sceneType) " +
            "AND r.expectedPriceTier IN :tiers " +
            "ORDER BY (r.lat - :centerLat) * (r.lat - :centerLat) " +
            "       + (r.lng - :centerLng) * (r.lng - :centerLng) ASC, r.id ASC")
    List<MatchingRequest> findOpenCandidates(@Param("latMin") BigDecimal latMin,
                                             @Param("latMax") BigDecimal latMax,
                                             @Param("lngMin") BigDecimal lngMin,
                                             @Param("lngMax") BigDecimal lngMax,
                                             @Param("excludeUserId") Long excludeUserId,
                                             @Param("sceneType") SceneType sceneType,
                                             @Param("tiers") Collection<PriceTier> tiers,
                                             @Param("centerLat") BigDecimal centerLat,
                                             @Param("centerLng") BigDecimal centerLng,
                                             Pageable pageable);

    /**
     * 「距离不限」模式候选：与 {@link #findOpenCandidates} 同签名/同 JPQL，但<b>不含 lat/lng bbox 过滤</b>，
     * 返回全部 OPEN 候选（排除自己 + scene/tier 下推），按平面近似距离 ASC, id ASC 排序。
     * 上线初期用户稀疏、需跨城/跨省运营时使用：不限距离但仍按距离就近排序。
     * 候选用 {@link Pageable} 截断到 nearby.candidate.limit；最终距离仍走应用层 Haversine 精算（仅排序/展示，不裁圆）。
     */
    @Query("SELECT r FROM MatchingRequest r WHERE r.status = com.sanshuiyuan.matching.request.domain.RequestStatus.OPEN " +
            "AND r.userId <> :excludeUserId " +
            "AND (:sceneType IS NULL OR r.sceneType = :sceneType) " +
            "AND r.expectedPriceTier IN :tiers " +
            "ORDER BY (r.lat - :centerLat) * (r.lat - :centerLat) " +
            "       + (r.lng - :centerLng) * (r.lng - :centerLng) ASC, r.id ASC")
    List<MatchingRequest> findAllOpenCandidates(@Param("excludeUserId") Long excludeUserId,
                                                @Param("sceneType") SceneType sceneType,
                                                @Param("tiers") Collection<PriceTier> tiers,
                                                @Param("centerLat") BigDecimal centerLat,
                                                @Param("centerLng") BigDecimal centerLng,
                                                Pageable pageable);
}
