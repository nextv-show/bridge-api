package com.sanshuiyuan.asset.application;

import com.sanshuiyuan.asset.application.event.OwnerRoleGrantRequested;
import com.sanshuiyuan.asset.application.event.RebateFreezeRequested;
import com.sanshuiyuan.asset.domain.*;
import com.sanshuiyuan.asset.infra.repository.DeviceAssetRepository;
import com.sanshuiyuan.asset.infra.repository.OrderRepository;
import com.sanshuiyuan.asset.infra.repository.SkuRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class PayCallbackUseCase {

    private final OrderRepository orderRepository;
    private final DeviceAssetRepository deviceAssetRepository;
    private final SkuRepository skuRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final JdbcTemplate jdbcTemplate;

    public PayCallbackUseCase(OrderRepository orderRepository,
                              DeviceAssetRepository deviceAssetRepository,
                              SkuRepository skuRepository,
                              ApplicationEventPublisher eventPublisher,
                              JdbcTemplate jdbcTemplate) {
        this.orderRepository = orderRepository;
        this.deviceAssetRepository = deviceAssetRepository;
        this.skuRepository = skuRepository;
        this.eventPublisher = eventPublisher;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void handleCallback(String transactionId, Long orderId, String rawBody) {
        // Idempotency check using payment_inbox
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment_inbox WHERE transaction_id = ?",
                Integer.class, transactionId);
        
        if (count != null && count > 0) {
            return; // Already processed
        }

        jdbcTemplate.update(
                "INSERT INTO payment_inbox (transaction_id, raw_body) VALUES (?, ?)",
                transactionId, rawBody);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        if (order.getStatus() != OrderStatus.PENDING_PAY) {
            return; // Already paid or closed
        }

        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(LocalDateTime.now());
        order.setWxTransactionId(transactionId);
        orderRepository.save(order);

        Sku sku = skuRepository.findById(order.getSkuId())
                .orElseThrow(() -> new RuntimeException("Sku not found: " + order.getSkuId()));

        // [废弃-024] 本路径写的是 asset_db.device_assets（asset-service 数据源），与真正在用的
        // core_db.device_assets（admin V074 建、admin/h5 共用）不同库且当前无客户端、行数为 0。
        // 认购已统一收敛到 h5-service（H5/小程序/App），资产入库改由 024（h5-service 在 PAID 事务
        // 直写 core_db.device_assets）负责。此处保留不删，待 005/后续清理 asset-service 旧 /orders 路径。
        // Create DeviceAsset(s) based on quantity
        for (int i = 0; i < order.getQty(); i++) {
            DeviceAsset asset = new DeviceAsset();
            asset.setUserId(order.getUserId());
            asset.setOrderId(order.getId());
            asset.setModel(sku.getName());
            asset.setPurchasedAt(LocalDateTime.now());
            asset.setStage(Stage.PENDING_MATCH); // Initial state
            asset.setCumulativeIncomeCents(0L);
            asset.setRoiBp(0);
            deviceAssetRepository.save(asset);
        }

        // D.2.4: Grant the OWNER role only after this payment transaction commits, asynchronously,
        // so a user-service outage can never roll back or block the payment main transaction.
        eventPublisher.publishEvent(new OwnerRoleGrantRequested(order.getUserId()));

        // 推荐返利（一次性实物销售介绍费）：同 OwnerRole 的「提交后异步」模式触发冻结。
        // 被推荐人 = 购机下单人；关系链查询 + SKU 费率快照 + 冻结全部推迟到事务提交后异步执行，
        // 取关系链的 user-service 故障绝不可阻塞或回滚本支付主事务。「每人仅一次」由领域服务封顶。
        eventPublisher.publishEvent(
                new RebateFreezeRequested(order.getId(), order.getUserId(), order.getSkuId()));
    }
}
