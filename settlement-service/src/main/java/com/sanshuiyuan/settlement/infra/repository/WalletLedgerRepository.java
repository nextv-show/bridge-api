package com.sanshuiyuan.settlement.infra.repository;

import com.sanshuiyuan.settlement.domain.WalletLedger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface WalletLedgerRepository extends JpaRepository<WalletLedger, Long> {
    List<WalletLedger> findByUserIdAndCreatedAtBetween(Long userId, LocalDateTime from, LocalDateTime to);
}
