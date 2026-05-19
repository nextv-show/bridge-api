package com.sanshuiyuan.asset.application;

import com.sanshuiyuan.asset.domain.*;
import com.sanshuiyuan.asset.infra.client.UserServiceClient;
import com.sanshuiyuan.asset.infra.repository.DeviceAssetRepository;
import com.sanshuiyuan.asset.infra.repository.OrderRepository;
import com.sanshuiyuan.asset.infra.repository.SkuRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class PayCallbackUseCase {

    private final OrderRepository orderRepository;
    private final DeviceAssetRepository deviceAssetRepository;
    private final SkuRepository skuRepository;
    private final UserServiceClient userServiceClient;
    private final JdbcTemplate jdbcTemplate;

    public PayCallbackUseCase(OrderRepository orderRepository,
                              DeviceAssetRepository deviceAssetRepository,
                              SkuRepository skuRepository,
                              UserServiceClient userServiceClient,
                              JdbcTemplate jdbcTemplate) {
        this.orderRepository = orderRepository;
        this.deviceAssetRepository = deviceAssetRepository;
        this.skuRepository = skuRepository;
        this.userServiceClient = userServiceClient;
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

        // Add OWNER role to user
        try {
            userServiceClient.addOwnerRole(order.getUserId());
        } catch (Exception e) {
            // Log error, maybe retry later
            System.err.println("Failed to add OWNER role for user " + order.getUserId() + ": " + e.getMessage());
        }
    }
}
