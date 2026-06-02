package com.sanshuiyuan.cend.checkout.infra.repository;

import com.sanshuiyuan.cend.checkout.domain.CendOrder;
import com.sanshuiyuan.cend.checkout.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CendOrderRepository extends JpaRepository<CendOrder, Long> {

    Optional<CendOrder> findByOrderNo(String orderNo);

    Optional<CendOrder> findFirstByOpenidAndSpecIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
            String openid, String specId, OrderStatus status, LocalDateTime after);

    List<CendOrder> findByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime before);

    // 主动查单对账：取某状态全部订单（在内存按 createdAt 过滤，兼容 createdAt 可能为 null 的情况）。
    List<CendOrder> findByStatus(OrderStatus status);

    // Spec 106: 我的订单列表查询（按 openid 分页，created_at 降序）
    org.springframework.data.domain.Page<CendOrder> findByOpenidOrderByCreatedAtDesc(
            String openid, org.springframework.data.domain.Pageable pageable);

    // Spec 015: 批量按 openid + 状态查订单（判断被推荐人是否已购买；按 openid 单点匹配，不涉及关系链层级）
    List<CendOrder> findByOpenidInAndStatus(Collection<String> openids, OrderStatus status);
}
