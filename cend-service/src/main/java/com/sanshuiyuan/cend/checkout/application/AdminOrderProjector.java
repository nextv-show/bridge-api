package com.sanshuiyuan.cend.checkout.application;

import com.sanshuiyuan.cend.checkout.domain.CendOrder;
import com.sanshuiyuan.cend.checkout.domain.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 在 h5_orders 写入的同一事务内，向 admin 的 {@code orders} 投影一行（按 h5_order_no 幂等 upsert），
 * 并保证买家在 admin 的 {@code users} 表存在（select-then-insert，应用层去重，不依赖 DB 唯一约束）。
 *
 * <p>h5_orders / orders / users / h5_users / skus 同库（h5_db），故可直接用 JdbcTemplate 跨表写。
 *
 * <p>投影失败仅记录日志、绝不抛出，避免影响主支付/关单/退款流程。
 */
@Component
public class AdminOrderProjector {

    private static final Logger log = LoggerFactory.getLogger(AdminOrderProjector.class);

    private final JdbcTemplate jdbc;

    public AdminOrderProjector(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * H5 OrderStatus -> admin orders.status 字符串映射。
     */
    static String mapStatus(OrderStatus status) {
        return switch (status) {
            case PENDING_PAY -> "PENDING_PAY";
            case PAID -> "PAID";
            case CLOSED -> "CANCELLED";
            case REFUNDING -> "REFUNDING";
            case REFUNDED -> "REFUNDED";
        };
    }

    public void project(CendOrder order) {
        try {
            long userId = resolveUserId(order.getOpenid());
            long skuId = resolveSkuId(order.getModelCode());
            String adminStatus = mapStatus(order.getStatus());
            // admin paymentDisplay 只识别大写枚举（WECHAT/ALIPAY…），h5 存的是小写 channel，统一转大写。
            String paymentMethod = order.getPaymentChannel() == null
                    ? null : order.getPaymentChannel().toUpperCase();
            // 下单刚 save 时实体的 createdAt 仍为 null（@Column insertable=false，DB 默认值未回填），
            // orders.created_at NOT NULL，故回退 now()。
            Timestamp createdAt = toTs(order.getCreatedAt() != null ? order.getCreatedAt() : LocalDateTime.now());
            Timestamp paidAt = toTs(order.getPaidAt());
            Timestamp cancelledAt = "CANCELLED".equals(adminStatus) ? toTs(order.getClosedAt()) : null;
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());

            jdbc.update(
                    "INSERT INTO orders (h5_order_no, user_id, sku_id, qty, amount_cents, status, channel, " +
                            "payment_method, wx_transaction_id, address_snapshot, created_at, paid_at, cancelled_at, updated_at) " +
                            "VALUES (?, ?, ?, 1, ?, ?, 'H5', ?, ?, '{}', ?, ?, ?, ?) " +
                            "ON DUPLICATE KEY UPDATE status = VALUES(status), payment_method = VALUES(payment_method), " +
                            "wx_transaction_id = VALUES(wx_transaction_id), paid_at = VALUES(paid_at), " +
                            "cancelled_at = VALUES(cancelled_at), updated_at = VALUES(updated_at)",
                    order.getOrderNo(), userId, skuId, order.getAmountCents(), adminStatus,
                    paymentMethod, order.getWxTransactionId(), createdAt, paidAt, cancelledAt, now);
        } catch (Exception e) {
            log.error("admin orders 投影失败 orderNo={}: {}", order.getOrderNo(), e.getMessage(), e);
        }

        // 024：认购支付完成（PAID）→ 资产入库 device_assets(PENDING_MATCH)，作为 002 撮合对象。
        // 多渠道（H5/小程序/App）均经 completePaid → 本投影，故渠道无关。best-effort + 独立 try
        // （与 admin orders 投影同哲学，不阻断支付主流程）；幂等靠 device_assets 的 UNIQUE(order_id)。
        if (order.getStatus() == OrderStatus.PAID) {
            try {
                projectDeviceAsset(order);
            } catch (Exception e) {
                log.error("device_asset 投影失败 orderNo={}: {}", order.getOrderNo(), e.getMessage(), e);
            }
        }
    }

    /**
     * 在 PAID 同事务内向 {@code device_assets}（h5_db 真表）建一条 PENDING_MATCH 资产。
     * order_id 取刚投影的 admin {@code orders}.id（按 h5_order_no 反查）；sn 待绑定为 NULL；
     * 收益列 0 为非空基线（不写收益，归 004）。幂等：UNIQUE(order_id) + ON DUPLICATE。
     */
    private void projectDeviceAsset(CendOrder order) {
        long userId = resolveUserId(order.getOpenid());
        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM orders WHERE h5_order_no = ?", Long.class, order.getOrderNo());
        if (ids.isEmpty()) {
            log.error("device_asset 投影：未找到 admin order h5_order_no={}", order.getOrderNo());
            return;
        }
        long adminOrderId = ids.get(0);
        Timestamp purchasedAt = toTs(order.getPaidAt() != null ? order.getPaidAt() : LocalDateTime.now());
        int n = jdbc.update(
                "INSERT INTO device_assets (user_id, order_id, sn, model, purchased_at, stage, " +
                        "cumulative_income_cents, roi_bp) VALUES (?, ?, NULL, ?, ?, 'PENDING_MATCH', 0, 0) " +
                        "ON DUPLICATE KEY UPDATE order_id = order_id",
                userId, adminOrderId, order.getModelCode(), purchasedAt);
        if (n > 0) {
            log.info("device_asset 入库 orderNo={} adminOrderId={} userId={}",
                    order.getOrderNo(), adminOrderId, userId);
        }
    }

    private long resolveUserId(String openid) {
        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM users WHERE openid = ?", Long.class, openid);
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        return insertUser(openid);
    }

    private long insertUser(String openid) {
        String nickname = "H5用户";
        String avatarUrl = null;
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT nickname, avatar_url FROM h5_users WHERE openid = ?", openid);
        if (!rows.isEmpty()) {
            Map<String, Object> row = rows.get(0);
            Object n = row.get("nickname");
            if (n != null && !n.toString().isBlank()) {
                nickname = n.toString();
            }
            Object a = row.get("avatar_url");
            avatarUrl = a != null ? a.toString() : null;
        }

        final String finalNickname = nickname;
        final String finalAvatarUrl = avatarUrl;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        // 幂等插入（依赖 admin V080 的 users.openid 唯一键 uk_openid，见 #19）：
        // ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id) —— 新插入返回新自增 id；
        // 并发首触命中唯一键时 LAST_INSERT_ID 回到既有行 id，不产重复行、不抛 DuplicateKey。
        // 唯一键未上线前该子句为无害空操作（openid 不冲突）。
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO users (openid, nickname, avatar_url, channel, tier, tags, status, kyc_status, " +
                            "created_at, updated_at) VALUES (?, ?, ?, 'WECHAT_MP', 'NORMAL', '', 'ACTIVE', 'NONE', NOW(), NOW()) " +
                            "ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, openid);
            ps.setString(2, finalNickname);
            ps.setString(3, finalAvatarUrl);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key != null) {
            return key.longValue();
        }
        // 兜底：个别驱动在 ODKU 命中更新分支可能不回传 key → 直接回查（此时行必存在）。
        List<Long> ids = jdbc.queryForList("SELECT id FROM users WHERE openid = ?", Long.class, openid);
        if (ids.isEmpty()) {
            throw new IllegalStateException("upsert admin users 后回查为空 openid=" + openid);
        }
        return ids.get(0);
    }

    private long resolveSkuId(String modelCode) {
        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM skus WHERE code = ?", Long.class, modelCode);
        return ids.isEmpty() ? 1L : ids.get(0);
    }

    private static Timestamp toTs(LocalDateTime dt) {
        return dt == null ? null : Timestamp.valueOf(dt);
    }
}
