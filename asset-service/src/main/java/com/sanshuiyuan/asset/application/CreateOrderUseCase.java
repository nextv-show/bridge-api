package com.sanshuiyuan.asset.application;

import com.sanshuiyuan.asset.domain.Order;
import com.sanshuiyuan.asset.domain.OrderStatus;
import com.sanshuiyuan.asset.domain.Sku;
import com.sanshuiyuan.asset.domain.SkuStatus;
import com.sanshuiyuan.asset.infra.repository.OrderRepository;
import com.sanshuiyuan.asset.infra.repository.SkuRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateOrderUseCase {

    private final OrderRepository orderRepository;
    private final SkuRepository skuRepository;

    public CreateOrderUseCase(OrderRepository orderRepository, SkuRepository skuRepository) {
        this.orderRepository = orderRepository;
        this.skuRepository = skuRepository;
    }

    @Transactional
    public Order createOrder(Long userId, Long skuId, Integer qty, String addressJson) {
        Sku sku = skuRepository.findByIdAndStatus(skuId, SkuStatus.ACTIVE)
                .orElseThrow(() -> new SkuUnavailableException("Invalid or inactive SKU"));

        Order order = new Order();
        order.setUserId(userId);
        order.setSkuId(skuId);
        order.setQty(qty);
        order.setAmountCents(sku.getPriceCents() * qty);
        order.setStatus(OrderStatus.PENDING_PAY);
        order.setAddressSnapshot(addressJson);
        
        // C.2.3: In real world, call WeChat Pay here to get prepay_id
        // For now, we set a mock prepay_id if it's a demo
        order.setWxPrepayId("mock_prepay_id_" + System.currentTimeMillis());

        return orderRepository.save(order);
    }
}
