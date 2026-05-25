package com.sanshuiyuan.h5.checkout.infra.repository;

import com.sanshuiyuan.h5.checkout.domain.H5Order;
import com.sanshuiyuan.h5.checkout.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface H5OrderRepository extends JpaRepository<H5Order, Long> {

    Optional<H5Order> findByOrderNo(String orderNo);

    Optional<H5Order> findFirstByOpenidAndSpecIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
            String openid, String specId, OrderStatus status, LocalDateTime after);

    List<H5Order> findByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime before);

    // Spec 106: 我的订单列表查询（按 openid 分页，created_at 降序）
    org.springframework.data.domain.Page<H5Order> findByOpenidOrderByCreatedAtDesc(
            String openid, org.springframework.data.domain.Pageable pageable);
}
