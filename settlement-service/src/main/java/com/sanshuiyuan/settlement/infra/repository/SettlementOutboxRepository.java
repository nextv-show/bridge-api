package com.sanshuiyuan.settlement.infra.repository;

import com.sanshuiyuan.settlement.domain.OutboxStatus;
import com.sanshuiyuan.settlement.domain.SettlementOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface SettlementOutboxRepository extends JpaRepository<SettlementOutbox, Long> {
    List<SettlementOutbox> findByStatusAndNextRunAtBefore(OutboxStatus status, LocalDateTime before);
}
