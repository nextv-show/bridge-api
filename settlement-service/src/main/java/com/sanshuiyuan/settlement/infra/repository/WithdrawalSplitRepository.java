package com.sanshuiyuan.settlement.infra.repository;

import com.sanshuiyuan.settlement.domain.SplitStatus;
import com.sanshuiyuan.settlement.domain.WithdrawalSplit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface WithdrawalSplitRepository extends JpaRepository<WithdrawalSplit, Long> {
    List<WithdrawalSplit> findByOrderId(Long orderId);
    List<WithdrawalSplit> findByStatusAndNextRunAtBefore(SplitStatus status, LocalDateTime before);
    List<WithdrawalSplit> findByExternalId(String externalId);
}
