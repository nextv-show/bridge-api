package com.sanshuiyuan.settlement.infra.repository;

import com.sanshuiyuan.settlement.domain.WithdrawalPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WithdrawalPolicyRepository extends JpaRepository<WithdrawalPolicy, Long> {
    Optional<WithdrawalPolicy> findTopByOrderByEffectiveFromDesc();
}
