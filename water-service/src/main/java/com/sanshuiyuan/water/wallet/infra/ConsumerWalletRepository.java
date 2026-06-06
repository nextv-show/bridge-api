package com.sanshuiyuan.water.wallet.infra;

import com.sanshuiyuan.water.wallet.domain.ConsumerWallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConsumerWalletRepository extends JpaRepository<ConsumerWallet, Long> {

    Optional<ConsumerWallet> findByUserId(Long userId);
}
