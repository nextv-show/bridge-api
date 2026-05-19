package com.sanshuiyuan.asset.application;

import com.sanshuiyuan.asset.domain.Order;
import com.sanshuiyuan.asset.domain.OrderStatus;
import com.sanshuiyuan.asset.infra.repository.OrderRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class CloseExpiredOrdersJob {

    private final OrderRepository orderRepository;

    public CloseExpiredOrdersJob(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void closeExpiredOrders() {
        LocalDateTime expiryTime = LocalDateTime.now().minusHours(24);
        List<Order> expiredOrders = orderRepository.findByStatusAndCreatedAtBefore(OrderStatus.PENDING_PAY, expiryTime);
        for (Order order : expiredOrders) {
            order.setStatus(OrderStatus.CLOSED);
            order.setClosedAt(LocalDateTime.now());
        }
        orderRepository.saveAll(expiredOrders);
    }
}
