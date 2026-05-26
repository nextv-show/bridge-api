package com.sanshuiyuan.admin.api.dto;

import java.util.List;
import java.util.Map;

public record OrderAdminDto(
        Long id,
        Long userId,
        Long skuId,
        String skuName,
        Integer qty,
        Long amountCents,
        String status,
        String channel,
        String paymentMethod,
        String paymentDisplay,
        String userName,
        String userPhoneMask,
        String userCity,
        String deviceSn,
        String deviceModel,
        String deviceStage,
        String installAddr,
        String shippingNo,
        String cancelReason,
        String createdAt,
        String paidAt,
        String shippedAt,
        String deliveredAt,
        String cancelledAt,
        String updatedAt,
        String paymentTxnId,
        Map<String, Object> addressSnapshot,
        List<OrderTimelineDto> timeline,
        Map<String, Object> userInfo,
        Map<String, Object> device,
        List<Map<String, Object>> breakdown
) {}
