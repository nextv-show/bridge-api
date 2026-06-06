package com.sanshuiyuan.settlement.infra.repository;

import com.sanshuiyuan.settlement.domain.OwnerWallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface OwnerWalletRepository extends JpaRepository<OwnerWallet, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OwnerWallet> findByUserId(Long userId);
}
