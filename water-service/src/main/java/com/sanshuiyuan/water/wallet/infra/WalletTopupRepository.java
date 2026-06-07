package com.sanshuiyuan.water.wallet.infra;

import com.sanshuiyuan.water.wallet.domain.WalletTopup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WalletTopupRepository extends JpaRepository<WalletTopup, Long> {

    Optional<WalletTopup> findByOutTradeNo(String outTradeNo);

    Optional<WalletTopup> findByWxTransactionId(String wxTransactionId);
}
