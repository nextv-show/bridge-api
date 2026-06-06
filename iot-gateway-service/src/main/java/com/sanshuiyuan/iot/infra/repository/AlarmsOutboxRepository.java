package com.sanshuiyuan.iot.infra.repository;

import com.sanshuiyuan.iot.domain.AlarmsOutbox;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlarmsOutboxRepository extends JpaRepository<AlarmsOutbox, Long> {

    Optional<AlarmsOutbox> findByAlarmId(Long alarmId);

    List<AlarmsOutbox> findByConsumedAtIsNullOrderByCreatedAtAsc(Pageable pageable);
}
