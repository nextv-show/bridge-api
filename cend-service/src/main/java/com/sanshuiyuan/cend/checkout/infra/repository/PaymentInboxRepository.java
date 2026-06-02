package com.sanshuiyuan.cend.checkout.infra.repository;

import com.sanshuiyuan.cend.checkout.domain.PaymentInbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentInboxRepository extends JpaRepository<PaymentInbox, Long> {

    Optional<PaymentInbox> findByTransactionId(String transactionId);
}
