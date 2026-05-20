package com.sanshuiyuan.asset.application;

import com.sanshuiyuan.asset.AbstractMysqlContainerTest;
import com.sanshuiyuan.asset.domain.Order;
import com.sanshuiyuan.asset.domain.OrderStatus;
import com.sanshuiyuan.asset.domain.SkuStatus;
import com.sanshuiyuan.asset.infra.repository.OrderRepository;
import com.sanshuiyuan.asset.infra.repository.SkuRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C.2.6: integration test for {@link CloseExpiredOrdersJob} against real MySQL (Testcontainers +
 * Flyway). A PENDING_PAY order older than 24h is closed by the job, while a freshly-created order
 * is untouched.
 *
 * <p>created_at is set by Order's @PrePersist to now(), so the "expired" order's timestamp is
 * back-dated via a native UPDATE before invoking the job.
 */
@SpringBootTest
class CloseExpiredOrdersJobIT extends AbstractMysqlContainerTest {

    @Autowired
    CloseExpiredOrdersJob job;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    SkuRepository skuRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        // Only clear orders this test owns; leave Flyway-seeded SKUs intact.
        orderRepository.deleteAll();
    }

    private Long seededSkuId() {
        return skuRepository.findByStatus(SkuStatus.ACTIVE).get(0).getId();
    }

    private Order newPendingOrder(Long skuId) {
        Order order = new Order();
        order.setUserId(99L);
        order.setSkuId(skuId);
        order.setQty(1);
        order.setAmountCents(19900L);
        order.setStatus(OrderStatus.PENDING_PAY);
        order.setAddressSnapshot("{\"city\":\"SH\"}");
        return orderRepository.save(order);
    }

    @Test
    void closeExpiredOrders_closesStaleAndKeepsFresh() {
        Long skuId = seededSkuId();

        Order stale = newPendingOrder(skuId);
        Order fresh = newPendingOrder(skuId);

        // Back-date the stale order's created_at to 25h ago (past the 24h expiry window).
        jdbcTemplate.update(
                "UPDATE orders SET created_at = (NOW() - INTERVAL 25 HOUR) WHERE id = ?",
                stale.getId());

        job.closeExpiredOrders();

        Order reloadedStale = orderRepository.findById(stale.getId()).orElseThrow();
        assertThat(reloadedStale.getStatus()).isEqualTo(OrderStatus.CLOSED);
        assertThat(reloadedStale.getClosedAt()).isNotNull();

        Order reloadedFresh = orderRepository.findById(fresh.getId()).orElseThrow();
        assertThat(reloadedFresh.getStatus()).isEqualTo(OrderStatus.PENDING_PAY);
        assertThat(reloadedFresh.getClosedAt()).isNull();
    }
}
