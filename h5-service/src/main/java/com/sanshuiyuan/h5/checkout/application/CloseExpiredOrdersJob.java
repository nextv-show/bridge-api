package com.sanshuiyuan.h5.checkout.application;

import com.sanshuiyuan.h5.checkout.domain.H5Order;
import com.sanshuiyuan.h5.checkout.domain.OrderStatus;
import com.sanshuiyuan.h5.checkout.infra.repository.H5OrderRepository;
import com.sanshuiyuan.h5.checkout.infra.wxpay.WxPayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class CloseExpiredOrdersJob {

    private static final Logger log = LoggerFactory.getLogger(CloseExpiredOrdersJob.class);

    private final H5OrderRepository orderRepo;
    private final WxPayClient wxPayClient;
    private final AdminOrderProjector adminOrderProjector;

    public CloseExpiredOrdersJob(H5OrderRepository orderRepo, WxPayClient wxPayClient,
                                 AdminOrderProjector adminOrderProjector) {
        this.orderRepo = orderRepo;
        this.wxPayClient = wxPayClient;
        this.adminOrderProjector = adminOrderProjector;
    }

    @Scheduled(fixedDelay = 60_000)
    public void closeExpired() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(30);
        List<H5Order> expired = orderRepo.findByStatusAndCreatedAtBefore(OrderStatus.PENDING_PAY, threshold);
        for (H5Order order : expired) {
            try {
                wxPayClient.closeOrder(order.getOrderNo());
                order.close();
                orderRepo.save(order);
                // 双写：投影关单状态（CANCELLED）到 admin orders 表。
                adminOrderProjector.project(order);
                log.info("Closed expired order {}", order.getOrderNo());
            } catch (Exception e) {
                log.error("Failed to close order {}: {}", order.getOrderNo(), e.getMessage());
            }
        }
    }
}
