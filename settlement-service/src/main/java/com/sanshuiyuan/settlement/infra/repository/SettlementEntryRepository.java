package com.sanshuiyuan.settlement.infra.repository;

import com.sanshuiyuan.settlement.domain.SettlementEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface SettlementEntryRepository extends JpaRepository<SettlementEntry, Long> {
    List<SettlementEntry> findByBillId(Long billId);
    List<SettlementEntry> findBySnAndPostedAtBetween(String sn, LocalDateTime from, LocalDateTime to);
}
