package com.sanshuiyuan.iot.infra.repository;

import com.sanshuiyuan.iot.domain.TelemetrySampleFlow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TelemetryFlowRepository extends JpaRepository<TelemetrySampleFlow, Long> {

    List<TelemetrySampleFlow> findBySessionIdOrderBySampledAtAsc(Long sessionId);

    @Query("SELECT MAX(t.litersMilli) FROM TelemetrySampleFlow t WHERE t.sessionId = ?1")
    Long findMaxLitersBySession(Long sessionId);
}
