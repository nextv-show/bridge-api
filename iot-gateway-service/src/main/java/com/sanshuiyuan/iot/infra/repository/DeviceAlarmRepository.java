package com.sanshuiyuan.iot.infra.repository;

import com.sanshuiyuan.iot.domain.DeviceAlarm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DeviceAlarmRepository extends JpaRepository<DeviceAlarm, Long> {

    Optional<DeviceAlarm> findByExternalEventId(String externalEventId);

    long countBySnAndResolvedAtIsNullAndRaisedAtAfter(String sn, LocalDateTime since);

    List<DeviceAlarm> findBySnAndResolvedAtIsNullOrderByRaisedAtDesc(String sn);
}
