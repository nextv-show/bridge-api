package com.sanshuiyuan.admin.infra.repository;

import com.sanshuiyuan.admin.domain.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("""
            SELECT o FROM Order o
            WHERE (:status IS NULL OR o.status = :status)
              AND (:channel IS NULL OR o.channel = :channel)
              AND (:paymentMethod IS NULL OR o.paymentMethod = :paymentMethod)
              AND (:startAt IS NULL OR o.createdAt >= :startAt)
              AND (:endAt IS NULL OR o.createdAt < :endAt)
              AND (:keyword IS NULL OR
                   CAST(o.id AS string) LIKE :keyword OR
                   CAST(o.userId AS string) LIKE :keyword OR
                   CAST(o.skuId AS string) LIKE :keyword OR
                   o.wxTransactionId LIKE :keyword OR
                   o.shippingNo LIKE :keyword OR
                   o.cancelReason LIKE :keyword)
            """)
    Page<Order> search(@Param("status") Order.Status status,
                       @Param("channel") String channel,
                       @Param("paymentMethod") String paymentMethod,
                       @Param("keyword") String keyword,
                       @Param("startAt") LocalDateTime startAt,
                       @Param("endAt") LocalDateTime endAt,
                       Pageable pageable);

    long countByStatus(Order.Status status);

    @Query("SELECT COALESCE(SUM(o.amountCents), 0) FROM Order o WHERE o.status = 'PAID'")
    long sumPaidAmountCents();

    @Query("SELECT COUNT(o) FROM Order o WHERE o.status = 'PAID'")
    long countPaid();

    /** 按 userId 批量聚合已支付订单数与 GMV。返回 [userId, count, sumAmountCents]。 */
    @Query("""
            SELECT o.userId, COUNT(o), COALESCE(SUM(o.amountCents), 0)
            FROM Order o
            WHERE o.userId IN :ids AND o.status = 'PAID'
            GROUP BY o.userId
            """)
    List<Object[]> aggregateByUserIds(@Param("ids") Collection<Long> ids);

    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);
}
