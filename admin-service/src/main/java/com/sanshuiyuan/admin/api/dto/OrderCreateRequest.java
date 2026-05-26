package com.sanshuiyuan.admin.api.dto;

import java.util.Map;

public record OrderCreateRequest(
        Long userId,
        Long skuId,
        Integer qty,
        Long amountCents,
        String channel,
        String paymentMethod,
        String shippingNo,
        String addressSnapshot,
        Long deviceAssetId,
        String paymentTxnId,
        String createdAt,
        String paidAt,
        Map<String, Object> extra
) {}
