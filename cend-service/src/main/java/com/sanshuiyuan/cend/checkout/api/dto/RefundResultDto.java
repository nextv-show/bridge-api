package com.sanshuiyuan.cend.checkout.api.dto;

public record RefundResultDto(
    String refundNo,
    String status,
    long amountCents,
    String refundedAt
) {}
