package com.sanshuiyuan.iot.infra.repository;

import com.sanshuiyuan.iot.domain.TelemetrySampleTds;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TelemetryTdsRepository extends JpaRepository<TelemetrySampleTds, Long> {

    Optional<TelemetrySampleTds> findTopBySnOrderBySampledAtDesc(String sn);
}
