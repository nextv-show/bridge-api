package com.sanshuiyuan.asset.infra.repository;

import com.sanshuiyuan.asset.domain.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    Optional<Refund> findByOrderId(Long orderId);

    Optional<Refund> findByRefundNo(String refundNo);
}
