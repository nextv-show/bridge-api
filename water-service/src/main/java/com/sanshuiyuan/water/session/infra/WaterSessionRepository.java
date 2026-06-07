package com.sanshuiyuan.water.session.infra;

import com.sanshuiyuan.water.session.domain.WaterSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WaterSessionRepository extends JpaRepository<WaterSession, Long> {

    Optional<WaterSession> findByIdAndUserId(Long id, Long userId);

    @Query("SELECT s FROM WaterSession s WHERE s.sn = :sn AND s.status = 'ACTIVE'")
    Optional<WaterSession> findActiveBySn(@Param("sn") String sn);

    List<WaterSession> findByUserIdOrderByStartedAtDesc(Long userId);

    /** 超时兜底扫描：仍 ACTIVE 且开启时间早于阈值的会话。 */
    @Query("SELECT s FROM WaterSession s WHERE s.status = 'ACTIVE' AND s.startedAt < :before")
    List<WaterSession> findTimedOut(@Param("before") LocalDateTime before);

    /**
     * 结算关闭：仅当会话仍 ACTIVE 且版本匹配时置 CLOSED。返回 0 表示已被关闭（幂等）。
     * 原生 SQL 直写 ended_at（实体上为 insertable/updatable=false）。
     */
    @Modifying
    @Query(value = "UPDATE water_sessions SET status='CLOSED', ended_at=NOW(), "
            + "total_liters_milli=:liters, total_amount_cents=:amount, end_reason=:reason, "
            + "version=version+1 WHERE id=:id AND status='ACTIVE' AND version=:version",
            nativeQuery = true)
    int closeActive(@Param("id") Long id, @Param("liters") long litersMilli,
                    @Param("amount") long amountCents, @Param("reason") String endReason,
                    @Param("version") int version);

    /** 中止：下发失败时把 ACTIVE 会话置 ABORTED。 */
    @Modifying
    @Query(value = "UPDATE water_sessions SET status='ABORTED', ended_at=NOW(), end_reason='ERROR', "
            + "version=version+1 WHERE id=:id AND status='ACTIVE'", nativeQuery = true)
    int markAborted(@Param("id") Long id);
}
