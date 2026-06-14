package com.sanshuiyuan.cend.myorders.application;

import com.sanshuiyuan.cend.checkout.domain.CendOrder;
import com.sanshuiyuan.cend.checkout.domain.OrderStatus;
import com.sanshuiyuan.cend.checkout.infra.repository.CendOrderRepository;
import com.sanshuiyuan.cend.common.BizException;
import com.sanshuiyuan.cend.common.ErrorCode;
import com.sanshuiyuan.cend.identity.IdentityResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * 删除订单（物理删除）。仅允许删除已关闭或已退款的订单，且只能删除当前自然人名下的订单。
 */
@Service
@Transactional
public class DeleteOrderUseCase {

    private final CendOrderRepository orderRepository;
    private final IdentityResolver identityResolver;

    public DeleteOrderUseCase(CendOrderRepository orderRepository,
                              IdentityResolver identityResolver) {
        this.orderRepository = orderRepository;
        this.identityResolver = identityResolver;
    }

    public void execute(Long orderId, String openid) {
        CendOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BizException(ErrorCode.ORDER_NOT_FOUND));

        // 归属校验（按自然人聚合）：订单 openid 必须属于当前用户名下。
        Set<String> owned = identityResolver.resolveOwnedOpenids(openid);
        if (!owned.contains(order.getOpenid())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }

        // 状态白名单：仅已关闭 / 已退款的订单可删除。
        OrderStatus status = order.getStatus();
        if (status != OrderStatus.CLOSED && status != OrderStatus.REFUNDED) {
            throw new BizException(ErrorCode.ORDER_STATUS_CONFLICT, "仅已关闭或已退款的订单可删除");
        }

        orderRepository.delete(order);
    }
}
