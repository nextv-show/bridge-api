package com.sanshuiyuan.cend.myorders;

import java.util.List;

/**
 * 112：订单安装进度聚合响应。
 *
 * <p>Timeline 为固定 9 步结构（PAID … STAGE_1），前端直接渲染 9 步，
 * 由后端标记每一步是否已到达（{@code time != null}）与当前所处步骤（{@code active}）。
 *
 * @param orderNo         订单号（h5_order_no）
 * @param orderStatus     h5_orders.status
 * @param deviceAssetId   设备资产 id（未匹配为 null）
 * @param deviceSn        设备 SN（未绑定为 null）
 * @param deviceStage     设备资产 stage（PENDING_MATCH/LOCKED/PENDING_ACTIVATE/STAGE_1）
 * @param logisticsStatus 物流工单状态（无工单为 null）
 * @param stages          固定 9 步时间线
 */
public record OrderProgressResponse(
        String orderNo,
        String orderStatus,
        Long deviceAssetId,
        String deviceSn,
        String deviceStage,
        String logisticsStatus,
        List<TimelineStage> stages
) {

    /**
     * 时间线单步。
     *
     * @param key    唯一标识（PAID/PENDING_MATCH/LOCKED/PENDING_SHIP/SHIPPED/DELIVERED/INSTALLED/PENDING_ACTIVATE/STAGE_1）
     * @param label  中文标签
     * @param time   ISO 时间字符串；{@code null} 表示该步尚未到达
     * @param active 是否为当前所处步骤（最远已到达的一步，全程唯一）
     */
    public record TimelineStage(
            String key,
            String label,
            String time,
            boolean active
    ) {}
}
