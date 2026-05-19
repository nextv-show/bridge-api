package com.sanshuiyuan.asset.infra.repository;

import com.sanshuiyuan.asset.domain.Order;
import com.sanshuiyuan.asset.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserIdAndStatus(Long userId, OrderStatus status);
    Optional<Order> findByIdAndUserId(Long id, Long userId);
    List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime before);
}
