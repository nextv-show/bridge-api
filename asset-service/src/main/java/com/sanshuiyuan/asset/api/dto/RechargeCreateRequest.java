package com.sanshuiyuan.asset.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 创建充值单。金额以 cents 提交（起充 ¥100 = 10000）。points/liters 为档位赠送。 */
public record RechargeCreateRequest(
    @NotNull @Min(10000) Long amountCents,
    Integer points,
    Integer liters,
    String payment
) {}
