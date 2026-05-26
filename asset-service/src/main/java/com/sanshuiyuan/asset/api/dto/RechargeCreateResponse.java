package com.sanshuiyuan.asset.api.dto;

/** 创建充值单结果。status=PENDING_PAY；真支付参数（wx jsapi）待接入后追加。 */
public record RechargeCreateResponse(
    long rechargeId,
    long amountCents,
    int points,
    int liters,
    String status
) {}
