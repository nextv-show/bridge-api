package com.sanshuiyuan.settlement.infra.repository;

import com.sanshuiyuan.settlement.domain.ReconciliationAlert;
import com.sanshuiyuan.settlement.domain.ReconciliationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ReconciliationAlertRepository extends JpaRepository<ReconciliationAlert, Long> {
    Optional<ReconciliationAlert> findByDate(LocalDate date);

    List<ReconciliationAlert> findByStatusOrderByDateDesc(ReconciliationStatus status);
}
