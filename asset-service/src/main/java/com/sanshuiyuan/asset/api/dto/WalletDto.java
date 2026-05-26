package com.sanshuiyuan.asset.api.dto;

/** 钱包视图。金额以 cents 传输，由前端换算为元展示。 */
public record WalletDto(
    long balanceCents,
    int points,
    int litersQuota,
    long dailyAvgCents,
    Long lastRechargeCents,
    String lastRechargeAt
) {}
