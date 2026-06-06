package com.sanshuiyuan.iot.infra.repository;

import com.sanshuiyuan.iot.domain.TelemetrySampleFilter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TelemetryFilterRepository extends JpaRepository<TelemetrySampleFilter, Long> {

    Optional<TelemetrySampleFilter> findTopBySnOrderBySampledAtDesc(String sn);
}
