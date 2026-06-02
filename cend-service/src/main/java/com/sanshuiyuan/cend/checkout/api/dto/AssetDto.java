package com.sanshuiyuan.cend.checkout.api.dto;

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
