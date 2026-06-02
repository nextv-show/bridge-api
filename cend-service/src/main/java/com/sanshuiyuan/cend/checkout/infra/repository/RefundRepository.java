package com.sanshuiyuan.cend.checkout.infra.repository;

import com.sanshuiyuan.cend.checkout.domain.Refund;
import com.sanshuiyuan.cend.checkout.domain.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    Optional<Refund> findByOrderId(Long orderId);

    Optional<Refund> findByRefundNo(String refundNo);

    boolean existsByOrderIdAndStatusIn(Long orderId, Iterable<RefundStatus> statuses);
}
