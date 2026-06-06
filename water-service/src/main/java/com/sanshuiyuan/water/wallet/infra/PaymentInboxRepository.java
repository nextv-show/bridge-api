package com.sanshuiyuan.water.wallet.infra;

import com.sanshuiyuan.water.wallet.domain.PaymentInbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentInboxRepository extends JpaRepository<PaymentInbox, Long> {

    Optional<PaymentInbox> findByTransactionId(String transactionId);
}
