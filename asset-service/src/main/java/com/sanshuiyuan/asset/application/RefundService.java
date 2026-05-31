package com.sanshuiyuan.asset.application;

import com.sanshuiyuan.asset.domain.Order;
import com.sanshuiyuan.asset.domain.OrderStatus;
import com.sanshuiyuan.asset.infra.repository.OrderRepository;
import com.sanshuiyuan.asset.rebate.application.RebateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 购机退款落地服务（实体商品买卖合同解除）。
 *
 * <p>退款成功时把订单置为 {@link OrderStatus#REFUND}，并联动取消该订单下的全部推荐返利：
 * 冷静期内 FROZEN→CANCELLED(REFUND_COOLDOWN)，冷静期后 CONFIRMED→CANCELLED(REFUND_POST_COOLDOWN)，
 * 已取消幂等跳过（取消逻辑全部委托 {@link RebateService#cancelForRefund}）。
 *
 * <p>幂等：已是 REFUND 的订单直接返回，避免重复触发返利取消。返利取消与订单置位同事务，保证一致。
 */
@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    private final OrderRepository orderRepository;
    private final RebateService rebateService;

    public RefundService(OrderRepository orderRepository, RebateService rebateService) {
        this.orderRepository = orderRepository;
        this.rebateService = rebateService;
    }

    /**
     * 处理购机退款成功：订单 PAID→REFUND，并取消其推荐返利。
     *
     * @param orderId 退款订单 id
     * @return 本次联动取消的推荐返利条数；幂等命中（订单已是 REFUND）返回 0
     */
    @Transactional
    public int handleRefundSucceeded(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        if (order.getStatus() == OrderStatus.REFUND) {
            return 0; // 幂等：已退款，不重复联动取消返利。
        }

        order.setStatus(OrderStatus.REFUND);
        orderRepository.save(order);

        // 退款联动取消推荐返利（实体商品买卖合同解除）。
        int cancelled = rebateService.cancelForRefund(orderId);
        log.info("购机退款 orderId={} 置 REFUND，联动取消推荐返利 {} 条", orderId, cancelled);
        return cancelled;
    }
}
