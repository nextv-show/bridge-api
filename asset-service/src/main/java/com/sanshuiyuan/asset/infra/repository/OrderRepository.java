package com.sanshuiyuan.asset.infra.repository;

import com.sanshuiyuan.asset.domain.Order;
import com.sanshuiyuan.asset.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserIdAndStatus(Long userId, OrderStatus status);
    Optional<Order> findByIdAndUserId(Long id, Long userId);
    List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime before);

    /**
     * 在给定 userId 集合中，筛出至少有一笔指定状态订单的 userId（去重）。
     * 供 user-service 批量回填「我的推荐」购买状态：传入 PAID 即得已购机用户子集。
     * 调用方须保证 {@code userIds} 非空（空集合不要查询，JPQL 空 IN 行为依方言而异）。
     */
    @Query("select distinct o.userId from Order o where o.userId in :userIds and o.status = :status")
    List<Long> findDistinctUserIdsByUserIdInAndStatus(@Param("userIds") Collection<Long> userIds,
                                                       @Param("status") OrderStatus status);
}
