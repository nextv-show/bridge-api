package com.sanshuiyuan.admin.infra.repository;

import com.sanshuiyuan.admin.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("SELECT COALESCE(SUM(o.amountCents), 0) FROM Order o WHERE o.status = 'PAID'")
    long sumPaidAmountCents();

    @Query("SELECT COUNT(o) FROM Order o WHERE o.status = 'PAID'")
    long countPaid();
}
