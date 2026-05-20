package com.sanshuiyuan.asset.application;

import com.sanshuiyuan.asset.AbstractMysqlContainerTest;
import com.sanshuiyuan.asset.domain.*;
import com.sanshuiyuan.asset.infra.client.UserServiceClient;
import com.sanshuiyuan.asset.infra.repository.DeviceAssetRepository;
import com.sanshuiyuan.asset.infra.repository.OrderRepository;
import com.sanshuiyuan.asset.infra.repository.SkuRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * D.2.5: integration tests for PayCallbackUseCase against real MySQL (Testcontainers + Flyway),
 * exercising the native JdbcTemplate payment_inbox idempotency together with JPA repositories.
 *
 * <p>Covers the payment main transaction: valid callback (order -> PAID, qty device_assets created
 * in PENDING_MATCH); replayed callback is idempotent (no duplicate assets); order not in
 * PENDING_PAY is skipped.
 *
 * <p>D.2.4: the OWNER-role grant is no longer synchronous inside the transaction — it is published
 * as an event and handled by {@link com.sanshuiyuan.asset.application.OwnerRoleGrantListener} with
 * {@code @TransactionalEventListener(AFTER_COMMIT) @Async}. So "user-service failure does not break
 * the main transaction" now holds by construction (the call happens on another thread after commit
 * and cannot roll the transaction back). We assert the main-flow effects synchronously and verify
 * the asynchronous grant eventually fires via Awaitility.
 */
@SpringBootTest
class PayCallbackUseCaseIT extends AbstractMysqlContainerTest {

    @Autowired
    PayCallbackUseCase payCallbackUseCase;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    DeviceAssetRepository deviceAssetRepository;

    @Autowired
    SkuRepository skuRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockBean
    UserServiceClient userServiceClient;

    @BeforeEach
    void cleanDatabase() {
        // @SpringBootTest does not roll back the @Transactional use-case commits, and the MySQL
        // container is shared across methods, so reset state explicitly for isolation. IMPORTANT:
        // only clear data this test owns (orders / device_assets / payment_inbox). Do NOT touch the
        // Flyway V004-seeded SKUs — deleting them here previously committed away the seed rows and
        // polluted SkuRepositoryIT.
        deviceAssetRepository.deleteAll();
        orderRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM payment_inbox");
    }

    private Long seededSkuId() {
        // Reuse a Flyway-seeded ACTIVE SKU instead of creating (and later deleting) our own, so the
        // seed data stays intact for other persistence tests.
        return skuRepository.findByStatus(SkuStatus.ACTIVE).get(0).getId();
    }

    private Order newOrder(Long skuId, int qty, OrderStatus status) {
        Order order = new Order();
        order.setUserId(42L);
        order.setSkuId(skuId);
        order.setQty(qty);
        order.setAmountCents(19900L * qty);
        order.setStatus(status);
        order.setAddressSnapshot("{\"city\":\"SH\"}");
        return orderRepository.save(order);
    }

    @Test
    void handleCallback_valid_marksPaidAndCreatesAssets() {
        Long skuId = seededSkuId();
        Sku sku = skuRepository.findById(skuId).orElseThrow();
        Order order = newOrder(skuId, 3, OrderStatus.PENDING_PAY);

        payCallbackUseCase.handleCallback("txn-valid-1", order.getId(), "{\"ok\":true}");

        Order reloaded = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(reloaded.getPaidAt()).isNotNull();
        assertThat(reloaded.getWxTransactionId()).isEqualTo("txn-valid-1");

        List<DeviceAsset> assets = deviceAssetRepository.findByUserId(42L);
        assertThat(assets).hasSize(3);
        assertThat(assets).allSatisfy(a -> {
            assertThat(a.getStage()).isEqualTo(Stage.PENDING_MATCH);
            assertThat(a.getOrderId()).isEqualTo(order.getId());
            assertThat(a.getModel()).isEqualTo(sku.getName());
            assertThat(a.getCumulativeIncomeCents()).isEqualTo(0L);
            assertThat(a.getRoiBp()).isEqualTo(0);
        });

        Integer inbox = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment_inbox WHERE transaction_id = ?",
                Integer.class, "txn-valid-1");
        assertThat(inbox).isEqualTo(1);

        // OWNER role grant is fired AFTER_COMMIT on an @Async thread; wait for it to be invoked.
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> verify(userServiceClient, times(1)).addOwnerRole(42L));
    }

    @Test
    void handleCallback_replay_isIdempotent() {
        Long skuId = seededSkuId();
        Order order = newOrder(skuId, 2, OrderStatus.PENDING_PAY);

        payCallbackUseCase.handleCallback("txn-replay-1", order.getId(), "{}");
        long afterFirst = deviceAssetRepository.findByUserId(order.getUserId()).size();

        // Replay with the same transaction id: payment_inbox already has the row, so the
        // method returns early without creating more assets.
        payCallbackUseCase.handleCallback("txn-replay-1", order.getId(), "{}");
        long afterReplay = deviceAssetRepository.findByUserId(order.getUserId()).size();

        assertThat(afterFirst).isEqualTo(2);
        assertThat(afterReplay).isEqualTo(afterFirst);

        Integer inbox = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment_inbox WHERE transaction_id = ?",
                Integer.class, "txn-replay-1");
        assertThat(inbox).isEqualTo(1);

        // Owner role granted only once (replay short-circuits before publishing the event).
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> verify(userServiceClient, times(1)).addOwnerRole(order.getUserId()));
    }

    @Test
    void handleCallback_orderNotPendingPay_skipsAssetCreation() {
        Long skuId = seededSkuId();
        Order order = newOrder(skuId, 4, OrderStatus.PAID);
        order.setPaidAt(LocalDateTime.now());
        orderRepository.save(order);

        long before = deviceAssetRepository.count();

        payCallbackUseCase.handleCallback("txn-notpending-1", order.getId(), "{}");

        // Inbox row is written before the status check, but no assets are created and the
        // order stays PAID. No OWNER-role event is published for a non-PENDING_PAY order.
        assertThat(deviceAssetRepository.count()).isEqualTo(before);
    }
}
