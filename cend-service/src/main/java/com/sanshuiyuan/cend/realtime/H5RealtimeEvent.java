package com.sanshuiyuan.cend.realtime;

import java.time.LocalDateTime;

/**
 * H5 实时推送事件：仅推送订单/返利状态变化，不包含任何 L3+ 关系信息。
 */
public record H5RealtimeEvent(
        String type,
        Long orderId,
        Long rebateId,
        String orderStatus,
        String rebateStatus,
        String reason,
        LocalDateTime occurredAt
) {
    public static H5RealtimeEvent order(Long orderId, String orderStatus, String reason) {
        return new H5RealtimeEvent("ORDER_STATUS", orderId, null, orderStatus, null, reason, LocalDateTime.now());
    }

    public static H5RealtimeEvent rebate(Long orderId, Long rebateId, String rebateStatus, String reason) {
        return new H5RealtimeEvent("REBATE_STATUS", orderId, rebateId, null, rebateStatus, reason, LocalDateTime.now());
    }
}
