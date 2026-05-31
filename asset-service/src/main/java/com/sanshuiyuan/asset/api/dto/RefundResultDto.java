package com.sanshuiyuan.asset.api.dto;

/**
 * 购机退款发起/查询结果。status 为小写（processing/success/failed），
 * refundedAt 仅成功时非空（ISO-8601 带时区偏移）。
 */
public record RefundResultDto(
        String refundNo,
        String status,
        long amountCents,
        String refundedAt
) {}
