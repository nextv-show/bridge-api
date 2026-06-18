package com.sanshuiyuan.cend.myorders;

import com.sanshuiyuan.cend.myorders.OrderProgressResponse.TimelineStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import org.springframework.jdbc.core.JdbcTemplate;
import com.sanshuiyuan.cend.identity.IdentityResolver;
import org.junit.jupiter.api.BeforeEach;

/**
 * 112 T3：OrderProgressService 单元测试（mock JdbcTemplate）。
 * 跨表（h5_orders/device_assets/matching_requests/logistics_orders/logistics_events）均在 core_db，
 * cend 测试容器无 admin schema，故以 mock 逐查询打桩，验证时间线组装与 active 标记，不走集成测试。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderProgressServiceTest {

    @Mock
    JdbcTemplate jdbc;

    @Mock
    IdentityResolver identityResolver;

    @InjectMocks
    OrderProgressService service;

    private static final String ORDER_NO = "H5ORD001";
    private static final String OPENID = "openid-1";

    /** 归属解析降级为「只看本端」：单 openid，使 h5_orders 查询保持 IN (?) 单参数。 */
    @BeforeEach
    void stubIdentity() {
        when(identityResolver.resolveOwnedOpenids(anyString())).thenReturn(Set.of(OPENID));
    }

    private static Timestamp ts(String v) {
        return Timestamp.valueOf(v);
    }

    /** 从 9 步时间线中按 key 取出某一步。 */
    private static TimelineStage stage(OrderProgressResponse r, String key) {
        return r.stages().stream().filter(s -> s.key().equals(key)).findFirst().orElseThrow();
    }

    private void mockOrderOwned(String paidAt) {
        when(jdbc.queryForList(contains("FROM h5_orders"), eq(ORDER_NO), eq(OPENID)))
                .thenReturn(List.of(Map.<String, Object>of("status", "PAID", "paid_at", ts(paidAt))));
    }

    @Test
    void normalFlow_FullTimeline() {
        mockOrderOwned("2026-06-01 10:00:00");
        // 设备已运营（STAGE_1）
        when(jdbc.queryForList(contains("FROM device_assets"), eq(ORDER_NO)))
                .thenReturn(List.of(Map.<String, Object>of("id", 10L, "sn", "SN-1", "stage", "STAGE_1")));
        // 匹配已确认
        when(jdbc.queryForList(contains("FROM matching_requests"), eq(10L)))
                .thenReturn(List.of(Map.<String, Object>of(
                        "status", "FULFILLED", "claim_confirmed_at", ts("2026-06-02 09:00:00"))));
        // 物流工单已安装
        when(jdbc.queryForList(contains("FROM logistics_orders"), eq(10L)))
                .thenReturn(List.of(Map.<String, Object>of(
                        "id", 77L, "status", "INSTALLED", "updated_at", ts("2026-06-05 18:00:00"))));
        // 物流事件全链路
        when(jdbc.queryForList(contains("FROM logistics_events"), eq(77L)))
                .thenReturn(List.of(
                        Map.<String, Object>of("event_type", "PENDING_SHIP", "occurred_at", ts("2026-06-03 08:00:00")),
                        Map.<String, Object>of("event_type", "SHIPPED", "occurred_at", ts("2026-06-03 12:00:00")),
                        Map.<String, Object>of("event_type", "DELIVERED", "occurred_at", ts("2026-06-04 15:00:00")),
                        Map.<String, Object>of("event_type", "INSTALLED", "occurred_at", ts("2026-06-05 18:00:00"))));

        OrderProgressResponse r = service.getProgress(ORDER_NO, OPENID).orElseThrow();

        assertThat(r.orderNo()).isEqualTo(ORDER_NO);
        assertThat(r.deviceAssetId()).isEqualTo(10L);
        assertThat(r.deviceSn()).isEqualTo("SN-1");
        assertThat(r.deviceStage()).isEqualTo("STAGE_1");
        assertThat(r.logisticsStatus()).isEqualTo("INSTALLED");
        assertThat(r.stages()).hasSize(9);

        // 各步时间均已填充
        assertThat(stage(r, "PAID").time()).isEqualTo("2026-06-01T10:00");
        assertThat(stage(r, "LOCKED").time()).isEqualTo("2026-06-02T09:00");
        assertThat(stage(r, "PENDING_SHIP").time()).isEqualTo("2026-06-03T08:00");
        assertThat(stage(r, "SHIPPED").time()).isEqualTo("2026-06-03T12:00");
        assertThat(stage(r, "DELIVERED").time()).isEqualTo("2026-06-04T15:00");
        assertThat(stage(r, "INSTALLED").time()).isEqualTo("2026-06-05T18:00");

        // active 唯一，且落在 STAGE_1
        assertThat(r.stages().stream().filter(TimelineStage::active).count()).isEqualTo(1);
        assertThat(stage(r, "STAGE_1").active()).isTrue();
    }

    @Test
    void noMatching_OnlyPaidStage() {
        mockOrderOwned("2026-06-01 10:00:00");
        when(jdbc.queryForList(contains("FROM device_assets"), eq(ORDER_NO)))
                .thenReturn(List.of()); // 无设备资产

        OrderProgressResponse r = service.getProgress(ORDER_NO, OPENID).orElseThrow();

        assertThat(r.deviceAssetId()).isNull();
        assertThat(r.deviceStage()).isNull();
        assertThat(r.logisticsStatus()).isNull();
        assertThat(r.stages()).hasSize(9);

        // 仅 PAID 有时间且 active，其余皆为未来步骤
        assertThat(stage(r, "PAID").time()).isEqualTo("2026-06-01T10:00");
        assertThat(stage(r, "PAID").active()).isTrue();
        assertThat(r.stages().stream().filter(s -> !s.key().equals("PAID")))
                .allSatisfy(s -> {
                    assertThat(s.time()).isNull();
                    assertThat(s.active()).isFalse();
                });
    }

    @Test
    void wrongOpenid_ReturnsEmpty() {
        when(jdbc.queryForList(contains("FROM h5_orders"), eq(ORDER_NO), eq(OPENID)))
                .thenReturn(List.of()); // openid 不匹配 → 查无此单

        Optional<OrderProgressResponse> r = service.getProgress(ORDER_NO, OPENID);

        assertThat(r).isEmpty();
    }

    @Test
    void hasDevicePendingMatch_NoLogistics_ActiveAtPendingMatch() {
        mockOrderOwned("2026-06-01 10:00:00");
        when(jdbc.queryForList(contains("FROM device_assets"), eq(ORDER_NO)))
                .thenReturn(List.of(Map.<String, Object>of("id", 10L, "sn", "SN-1", "stage", "PENDING_MATCH")));
        when(jdbc.queryForList(contains("FROM matching_requests"), eq(10L))).thenReturn(List.of());
        when(jdbc.queryForList(contains("FROM logistics_orders"), eq(10L))).thenReturn(List.of());

        OrderProgressResponse r = service.getProgress(ORDER_NO, OPENID).orElseThrow();

        // PAID 已完成（有时间、非 active），当前停在 PENDING_MATCH
        assertThat(stage(r, "PAID").time()).isEqualTo("2026-06-01T10:00");
        assertThat(stage(r, "PAID").active()).isFalse();
        assertThat(stage(r, "PENDING_MATCH").active()).isTrue();
        assertThat(stage(r, "LOCKED").time()).isNull();
        assertThat(stage(r, "LOCKED").active()).isFalse();
        assertThat(r.stages().stream().filter(TimelineStage::active).count()).isEqualTo(1);
    }

    @Test
    void logisticsInProgress_ActiveAtCurrentStatus() {
        mockOrderOwned("2026-06-01 10:00:00");
        when(jdbc.queryForList(contains("FROM device_assets"), eq(ORDER_NO)))
                .thenReturn(List.of(Map.<String, Object>of("id", 10L, "sn", "SN-1", "stage", "LOCKED")));
        when(jdbc.queryForList(contains("FROM matching_requests"), eq(10L)))
                .thenReturn(List.of(Map.<String, Object>of(
                        "status", "LOCKED", "claim_confirmed_at", ts("2026-06-02 09:00:00"))));
        when(jdbc.queryForList(contains("FROM logistics_orders"), eq(10L)))
                .thenReturn(List.of(Map.<String, Object>of(
                        "id", 77L, "status", "SHIPPED", "updated_at", ts("2026-06-03 12:00:00"))));
        when(jdbc.queryForList(contains("FROM logistics_events"), eq(77L)))
                .thenReturn(List.of(
                        Map.<String, Object>of("event_type", "PENDING_SHIP", "occurred_at", ts("2026-06-03 08:00:00")),
                        Map.<String, Object>of("event_type", "SHIPPED", "occurred_at", ts("2026-06-03 12:00:00"))));

        OrderProgressResponse r = service.getProgress(ORDER_NO, OPENID).orElseThrow();

        // 当前停在 SHIPPED；已到达节点有时间，未来节点为空
        assertThat(stage(r, "PENDING_SHIP").time()).isEqualTo("2026-06-03T08:00");
        assertThat(stage(r, "SHIPPED").time()).isEqualTo("2026-06-03T12:00");
        assertThat(stage(r, "SHIPPED").active()).isTrue();
        assertThat(stage(r, "DELIVERED").time()).isNull();
        assertThat(stage(r, "DELIVERED").active()).isFalse();
        assertThat(stage(r, "INSTALLED").time()).isNull();
        assertThat(stage(r, "INSTALLED").active()).isFalse();
        assertThat(r.stages().stream().filter(TimelineStage::active).count()).isEqualTo(1);
    }
}
