package com.sanshuiyuan.cend.checkout.infra.repository;

import com.sanshuiyuan.cend.AbstractMysqlContainerTest;
import com.sanshuiyuan.cend.checkout.domain.CendOrder;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T8a.6（cend-service 侧）：V018 迁移在 cend-service 实际连接库（h5_db）落库 ——
 * h5_orders 新增快照列存在，且关系链快照列可写可读（ddl-auto=validate 同时校验实体↔列一致）。
 */
@Tag("integration")
@DisabledIfEnvironmentVariable(named = "CI_SKIP_IT", matches = "true")
@SpringBootTest
class CendOrderReferralSnapshotIT extends AbstractMysqlContainerTest {

    @Autowired
    CendOrderRepository orderRepo;

    @Autowired
    EntityManager em;

    @Test
    void v018_addsSnapshotColumnsTo_h5Orders() {
        Number cols = (Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'h5_orders' " +
                "AND COLUMN_NAME IN ('inviter_id','grand_inviter_id')").getSingleResult();
        assertThat(cols.intValue()).isEqualTo(2);
    }

    @Test
    void snapshotReferral_isPersistedAndReloadable() {
        CendOrder order = CendOrder.create("REFTEST-0001", "openid-ref-1", "SPEC-1", "MODEL-1", 299900L, "WXPAY");
        order.snapshotReferral(14820L, 14808L);
        orderRepo.saveAndFlush(order);
        em.clear();

        CendOrder reloaded = orderRepo.findByOrderNo("REFTEST-0001").orElseThrow();
        assertThat(reloaded.getInviterId()).isEqualTo(14820L);
        assertThat(reloaded.getGrandInviterId()).isEqualTo(14808L);
    }

    @Test
    void snapshotReferral_naturalTraffic_isNullable() {
        CendOrder order = CendOrder.create("REFTEST-0002", "openid-ref-2", "SPEC-1", "MODEL-1", 299900L, "WXPAY");
        // 不调用 snapshotReferral：自然流量订单，快照列保持 null。
        orderRepo.saveAndFlush(order);
        em.clear();

        CendOrder reloaded = orderRepo.findByOrderNo("REFTEST-0002").orElseThrow();
        assertThat(reloaded.getInviterId()).isNull();
        assertThat(reloaded.getGrandInviterId()).isNull();
    }
}
