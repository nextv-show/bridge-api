package com.sanshuiyuan.cend.myorders;

import com.sanshuiyuan.cend.myorders.OrderProgressResponse.TimelineStage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 112：聚合查询订单全链路安装进度。
 *
 * <p>cend-service 的 JdbcTemplate 连接 core_db，可跨表读取 h5_orders / device_assets /
 * matching_requests / logistics_orders / logistics_events（同 {@code AdminOrderProjector} 模式）。
 *
 * <p>链路：h5_orders（归属 + 支付时间）→ device_assets（资产 stage）→ matching_requests（匹配确认时间）
 * → logistics_orders（物流状态）→ logistics_events（各物流节点时间）。任一环节缺失即停在对应步骤。
 */
@Component
public class OrderProgressService {

    /** 固定 9 步时间线定义：{key, label}，顺序即进度顺序。 */
    private static final String[][] STAGE_DEFS = {
            {"PAID", "订单已支付"},
            {"PENDING_MATCH", "等待设备匹配"},
            {"LOCKED", "设备已匹配"},
            {"PENDING_SHIP", "等待发货"},
            {"SHIPPED", "运输中"},
            {"DELIVERED", "已送达"},
            {"INSTALLED", "安装完成"},
            {"PENDING_ACTIVATE", "等待激活"},
            {"STAGE_1", "运营中"},
    };

    private final JdbcTemplate jdbc;

    public OrderProgressService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 返回订单安装进度。
     *
     * @param orderNo 订单号（h5_order_no）
     * @param openid  当前用户 openid（用于归属校验）
     * @return 进度数据；订单不存在或不属于该 openid 时返回 {@link Optional#empty()}
     */
    public Optional<OrderProgressResponse> getProgress(String orderNo, String openid) {
        // 1. 归属校验 + 取订单状态/支付时间
        List<Map<String, Object>> orderRows = jdbc.queryForList(
                "SELECT status, paid_at FROM h5_orders WHERE order_no = ? AND openid = ?", orderNo, openid);
        if (orderRows.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> order = orderRows.get(0);
        String orderStatus = str(order.get("status"));
        String paidAt = iso(order.get("paid_at"));

        // 2. 设备资产（经 admin orders 反查 h5_order_no）
        List<Map<String, Object>> deviceRows = jdbc.queryForList(
                "SELECT d.id AS id, d.sn AS sn, d.stage AS stage FROM device_assets d " +
                        "JOIN orders o ON d.order_id = o.id WHERE o.h5_order_no = ? ORDER BY d.id DESC LIMIT 1",
                orderNo);
        if (deviceRows.isEmpty()) {
            // 未匹配：仅 PAID 步骤，停在已支付。
            return Optional.of(new OrderProgressResponse(
                    orderNo, orderStatus, null, null, null, null,
                    assembleStages(paidAt, null, null, null, Map.of(), 0)));
        }
        Map<String, Object> device = deviceRows.get(0);
        Long deviceAssetId = lng(device.get("id"));
        String deviceSn = str(device.get("sn"));
        String deviceStage = str(device.get("stage"));

        // 3. 匹配请求（取最新一条的确认时间）
        List<Map<String, Object>> matchRows = jdbc.queryForList(
                "SELECT status, claim_confirmed_at FROM matching_requests " +
                        "WHERE device_asset_id = ? ORDER BY id DESC LIMIT 1", deviceAssetId);
        String matchConfirmedAt = matchRows.isEmpty() ? null : iso(matchRows.get(0).get("claim_confirmed_at"));

        // 4. 物流工单（取最新一条）
        List<Map<String, Object>> logiRows = jdbc.queryForList(
                "SELECT id, status, updated_at FROM logistics_orders " +
                        "WHERE device_asset_id = ? ORDER BY id DESC LIMIT 1", deviceAssetId);
        String logisticsStatus = null;
        String logisticsUpdatedAt = null;
        Map<String, String> eventTimes = Map.of();
        if (!logiRows.isEmpty()) {
            Map<String, Object> logi = logiRows.get(0);
            Long logisticsOrderId = lng(logi.get("id"));
            logisticsStatus = str(logi.get("status"));
            logisticsUpdatedAt = iso(logi.get("updated_at"));

            // 5. 物流事件（按发生时间升序，每种 event_type 取最早一条）
            List<Map<String, Object>> eventRows = jdbc.queryForList(
                    "SELECT event_type, occurred_at FROM logistics_events " +
                            "WHERE logistics_order_id = ? ORDER BY occurred_at ASC", logisticsOrderId);
            eventTimes = new HashMap<>();
            for (Map<String, Object> ev : eventRows) {
                String type = str(ev.get("event_type"));
                if (type != null) {
                    eventTimes.putIfAbsent(type, iso(ev.get("occurred_at")));
                }
            }
        }

        int current = computeCurrentIndex(deviceStage, logisticsStatus);
        List<TimelineStage> stages = assembleStages(
                paidAt, matchConfirmedAt, logisticsUpdatedAt, logisticsStatus, eventTimes, current);

        return Optional.of(new OrderProgressResponse(
                orderNo, orderStatus, deviceAssetId, deviceSn, deviceStage, logisticsStatus, stages));
    }

    /**
     * 计算当前所处步骤索引（最远已到达的一步）。
     * device_assets.stage 是主驱动；LOCKED 阶段进一步由物流状态细分到具体物流节点。
     */
    private int computeCurrentIndex(String deviceStage, String logisticsStatus) {
        if (deviceStage == null) {
            return 0; // PAID
        }
        return switch (deviceStage) {
            case "PENDING_MATCH" -> 1;
            case "LOCKED" -> logisticsStatus == null ? 2 : logisticsIndex(logisticsStatus);
            case "PENDING_ACTIVATE" -> 7;
            case "STAGE_1" -> 8;
            default -> 1;
        };
    }

    /** 物流状态 → 时间线索引（PENDING_SHIP..INSTALLED = 3..6）；CANCELLED/未知回退到 LOCKED(2)。 */
    private int logisticsIndex(String status) {
        return switch (status) {
            case "PENDING_SHIP" -> 3;
            case "SHIPPED" -> 4;
            case "DELIVERED" -> 5;
            case "INSTALLED" -> 6;
            default -> 2;
        };
    }

    /**
     * 组装 9 步时间线：填充各步时间，并按 {@code current} 标记唯一 active 步骤。
     * 已到达步骤 {@code time != null}；未到达步骤 {@code time == null}；PENDING_MATCH/
     * PENDING_ACTIVATE/STAGE_1 无独立时间戳，仅以 active 体现。
     */
    private List<TimelineStage> assembleStages(
            String paidAt, String matchConfirmedAt, String logisticsUpdatedAt,
            String logisticsStatus, Map<String, String> eventTimes, int current) {
        Map<String, String> times = new HashMap<>();
        times.put("PAID", paidAt);
        times.put("LOCKED", matchConfirmedAt);
        // 物流节点优先取事件时间；PENDING_SHIP 无事件时回退到工单 updated_at（仅当仍处于待发货）。
        times.put("PENDING_SHIP", eventTimes.getOrDefault("PENDING_SHIP",
                "PENDING_SHIP".equals(logisticsStatus) ? logisticsUpdatedAt : null));
        times.put("SHIPPED", eventTimes.get("SHIPPED"));
        times.put("DELIVERED", eventTimes.get("DELIVERED"));
        times.put("INSTALLED", eventTimes.get("INSTALLED"));

        List<TimelineStage> stages = new ArrayList<>(STAGE_DEFS.length);
        for (int i = 0; i < STAGE_DEFS.length; i++) {
            String key = STAGE_DEFS[i][0];
            String label = STAGE_DEFS[i][1];
            stages.add(new TimelineStage(key, label, times.get(key), i == current));
        }
        return stages;
    }

    private static String str(Object v) {
        return v == null ? null : v.toString();
    }

    private static Long lng(Object v) {
        return v == null ? null : ((Number) v).longValue();
    }

    /** 时间列 → ISO 字符串（如 {@code 2026-06-17T10:15:30}）。 */
    private static String iso(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Timestamp ts) {
            return ts.toLocalDateTime().toString();
        }
        if (v instanceof LocalDateTime dt) {
            return dt.toString();
        }
        return v.toString();
    }
}
