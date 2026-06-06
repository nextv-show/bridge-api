package com.sanshuiyuan.iot.infra.repository;

import com.sanshuiyuan.iot.domain.TelemetrySampleFlow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TelemetryFlowRepository extends JpaRepository<TelemetrySampleFlow, Long> {

    List<TelemetrySampleFlow> findBySessionIdOrderBySampledAtAsc(Long sessionId);

    @Query("SELECT MAX(t.litersMilli) FROM TelemetrySampleFlow t WHERE t.sessionId = ?1")
    Long findMaxLitersBySession(Long sessionId);

    Optional<TelemetrySampleFlow> findTopBySnOrderBySampledAtDesc(String sn);

    @Query("SELECT COALESCE(SUM(t.deltaMilli), 0) FROM TelemetrySampleFlow t WHERE t.sn = ?1 AND t.sampledAt > ?2")
    Long sumDeltaMilliBySnSince(String sn, LocalDateTime since);
}
