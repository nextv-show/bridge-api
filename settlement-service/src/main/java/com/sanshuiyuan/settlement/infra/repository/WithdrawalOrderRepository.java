package com.sanshuiyuan.settlement.infra.repository;

import com.sanshuiyuan.settlement.domain.WithdrawalOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WithdrawalOrderRepository extends JpaRepository<WithdrawalOrder, Long> {
    Optional<WithdrawalOrder> findByUserIdAndClientRequestId(Long userId, String clientRequestId);
    List<WithdrawalOrder> findByUserIdOrderByCreatedAtDesc(Long userId);
}
