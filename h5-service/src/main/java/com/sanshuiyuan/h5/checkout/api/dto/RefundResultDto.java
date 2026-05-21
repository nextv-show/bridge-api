package com.sanshuiyuan.h5.checkout.api.dto;

public record RefundResultDto(
    String refundNo,
    String status,
    long amountCents,
    String refundedAt
) {}
