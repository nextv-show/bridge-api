package com.sanshuiyuan.water.wallet.infra;

import com.sanshuiyuan.water.wallet.domain.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    List<WalletTransaction> findByUserIdOrderByCreatedAtDesc(Long userId);
}
