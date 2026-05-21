package com.sanshuiyuan.h5.checkout.api.dto;

public record OrderCreateResponse(
    Long orderId,
    String orderNo,
    Long amountCents,
    String specId,
    String modelCode,
    String status
) {}
