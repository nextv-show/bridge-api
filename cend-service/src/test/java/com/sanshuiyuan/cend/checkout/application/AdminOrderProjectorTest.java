package com.sanshuiyuan.cend.checkout.application;

import com.sanshuiyuan.cend.checkout.domain.CendOrder;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 024 Phase B：AdminOrderProjector 的 device_assets 入库逻辑单元测试（mock JdbcTemplate）。
 * device_assets 是 admin-service 拥有的 h5_db 真表，cend-service 测试容器无该表，故不走集成测试；
 * 真实 DB 的 INSERT/幂等在 Phase C 联调（真 h5_db 有 admin schema）验证。
 */
class AdminOrderProjectorTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AdminOrderProjector projector = new AdminOrderProjector(jdbc);

    private CendOrder paidOrder() {
        CendOrder o = CendOrder.create("H5TEST001", "openid-x", "home-std", "BR-H1", 460000L, "wechat");
        o.markPaid("wx-txn-1", "SN-PENDING-H5TEST001", LocalDateTime.now().plusHours(24)); // → PAID + paidAt
        return o;
    }

    @Test
    void paid_order_projects_pending_match_device_asset() {
        when(jdbc.queryForList(contains("FROM users"), eq(Long.class), any())).thenReturn(List.of(100L));
        when(jdbc.queryForList(contains("FROM skus"), eq(Long.class), any())).thenReturn(List.of(7L));
        when(jdbc.queryForList(contains("FROM orders WHERE h5_order_no"), eq(Long.class), any()))
                .thenReturn(List.of(500L));

        projector.project(paidOrder());

        // device_assets 入库：PENDING_MATCH + ON DUPLICATE 幂等；参数 userId=100、adminOrderId=500、model=BR-H1。
        verify(jdbc).update(
                argThat((String s) -> s.contains("INTO device_assets")
                        && s.contains("PENDING_MATCH") && s.contains("ON DUPLICATE KEY")),
                eq(100L), eq(500L), eq("BR-H1"), any());
    }

    @Test
    void pending_pay_order_does_not_project_device_asset() {
        CendOrder pending = CendOrder.create("H5TEST002", "openid-y", "home-std", "BR-H1", 460000L, "wechat");
        when(jdbc.queryForList(any(), eq(Long.class), any())).thenReturn(List.of(1L));

        projector.project(pending); // 状态 PENDING_PAY

        verify(jdbc, never()).update(
                argThat((String s) -> s.contains("INTO device_assets")), any(), any(), any(), any());
    }
}
