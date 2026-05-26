package com.sanshuiyuan.asset.infra.repository;

import com.sanshuiyuan.asset.domain.WalletRecharge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WalletRechargeRepository extends JpaRepository<WalletRecharge, Long> {
    Optional<WalletRecharge> findByIdAndUserId(Long id, Long userId);
}
