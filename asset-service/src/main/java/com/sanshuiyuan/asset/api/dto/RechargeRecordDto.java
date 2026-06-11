package com.sanshuiyuan.asset.api.dto;

import com.sanshuiyuan.asset.domain.WalletRecharge;

import java.time.format.DateTimeFormatter;

/**
 * 充值/账单流水记录视图（小程序「充值与账单」页）。
 * 金额以 cents 传输，由前端换算为元展示；时间为已格式化字符串。
 */
public record RechargeRecordDto(
    Long id,
    long amountCents,
    int pointsGranted,
    int litersGranted,
    String status,
    String payChannel,
    String createdAt,
    String paidAt
) {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static RechargeRecordDto from(WalletRecharge r) {
        return new RechargeRecordDto(
                r.getId(),
                r.getAmountCents() != null ? r.getAmountCents() : 0L,
                r.getPointsGranted() != null ? r.getPointsGranted() : 0,
                r.getLitersGranted() != null ? r.getLitersGranted() : 0,
                r.getStatus() != null ? r.getStatus().name() : null,
                r.getPayChannel(),
                r.getCreatedAt() != null ? r.getCreatedAt().format(TS) : null,
                r.getPaidAt() != null ? r.getPaidAt().format(TS) : null
        );
    }
}
