package com.sanshuiyuan.cend.checkout.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 110 SN 回写：matching-service 在设备履约/激活节点经 S2S 调用，把真实 SN 回写到 h5_orders.sn，
 * 替换支付完成时写入的占位 SN（{@code SN-PENDING-{orderNo}}）。
 *
 * <p>h5_orders / orders / device_assets 同库（core_db），故可直接用 JdbcTemplate 跨表查询
 * （与 {@link AdminOrderProjector} 同源 JdbcTemplate）。
 *
 * <p>链路：{@code device_assets.order_id → orders.id → orders.h5_order_no → h5_orders.order_no}。
 * 幂等：仅当 h5_orders.sn 仍为占位符（{@code LIKE 'SN-PENDING-%'}）时更新；已是真实 SN 则跳过。
 */
@Component
public class BindSnUseCase {

    private static final Logger log = LoggerFactory.getLogger(BindSnUseCase.class);

    private static final String SN_PLACEHOLDER_PREFIX = "SN-PENDING";

    private final JdbcTemplate jdbc;

    public BindSnUseCase(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 通过 device_asset_id 链路查询 order_no，幂等更新 h5_orders.sn。
     *
     * @return {@code true}=已回写一行；{@code false}=跳过（SN 为空/占位符、找不到设备或订单、h5_orders.sn 非占位符）
     */
    public boolean tryBindSn(long deviceAssetId, String sn) {
        // 1) SN 为空或仍是占位符 → 无真实 SN 可回写，跳过
        if (sn == null || sn.isBlank() || sn.startsWith(SN_PLACEHOLDER_PREFIX)) {
            log.debug("BindSn: device_asset_id={} 跳过：sn 为空或占位符（{}）", deviceAssetId, sn);
            return false;
        }

        // 2) device_assets.order_id（admin orders.id）
        List<Long> orderIds = jdbc.queryForList(
                "SELECT order_id FROM device_assets WHERE id = ?", Long.class, deviceAssetId);
        if (orderIds.isEmpty() || orderIds.get(0) == null) {
            log.debug("BindSn: device_asset_id={} 跳过：无 order_id（设备不存在或未关联订单）", deviceAssetId);
            return false;
        }
        long orderId = orderIds.get(0);

        // 3) orders.h5_order_no → h5_orders.order_no
        List<String> orderNos = jdbc.queryForList(
                "SELECT h5_order_no FROM orders WHERE id = ?", String.class, orderId);
        if (orderNos.isEmpty() || orderNos.get(0) == null || orderNos.get(0).isBlank()) {
            log.debug("BindSn: device_asset_id={} order_id={} 跳过：orders 无 h5_order_no", deviceAssetId, orderId);
            return false;
        }
        String orderNo = orderNos.get(0);

        // 4) 幂等回写：仅占位符可被覆盖
        int updated = jdbc.update(
                "UPDATE h5_orders SET sn = ? WHERE order_no = ? AND sn LIKE 'SN-PENDING-%'",
                sn, orderNo);
        if (updated == 1) {
            log.info("BindSn: device_asset_id={} order_no={} 回写真实 sn={}", deviceAssetId, orderNo, sn);
            return true;
        }
        log.debug("BindSn: device_asset_id={} order_no={} 跳过：h5_orders.sn 非占位符或订单不存在", deviceAssetId, orderNo);
        return false;
    }
}
