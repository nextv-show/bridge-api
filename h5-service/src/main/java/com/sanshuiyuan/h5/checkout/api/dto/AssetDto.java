package com.sanshuiyuan.h5.checkout.api.dto;

public record AssetDto(
    String orderNo,
    String modelName,
    long paidAmountCents,
    String payChannel,
    String cooldownEndAt,
    String orderStatus,
    String sn,
    String invoiceStatus,
    long cooldownRemainingSeconds
) {}
