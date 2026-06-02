package com.sanshuiyuan.cend.checkout.application;

import com.sanshuiyuan.cend.checkout.domain.CendOrder;
import com.sanshuiyuan.cend.checkout.infra.repository.CendOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 一次性回填：把所有现存 h5_orders 投影到 admin 的 orders 表（按 h5_order_no 幂等）。
 * 默认关闭，需显式设置 {@code h5.admin-order-backfill.enabled=true} 才会运行。
 */
@Component
@ConditionalOnProperty(name = "h5.admin-order-backfill.enabled", havingValue = "true")
public class AdminOrderBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminOrderBackfillRunner.class);

    private final CendOrderRepository orderRepo;
    private final AdminOrderProjector projector;

    public AdminOrderBackfillRunner(CendOrderRepository orderRepo, AdminOrderProjector projector) {
        this.orderRepo = orderRepo;
        this.projector = projector;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<CendOrder> all = orderRepo.findAll();
        log.info("admin orders 回填开始：共 {} 条 h5_orders", all.size());
        for (CendOrder order : all) {
            projector.project(order);
        }
        log.info("admin orders 回填完成：共处理 {} 条", all.size());
    }
}
