package com.sanshuiyuan.admin.infra.repository;

import com.sanshuiyuan.admin.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

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
