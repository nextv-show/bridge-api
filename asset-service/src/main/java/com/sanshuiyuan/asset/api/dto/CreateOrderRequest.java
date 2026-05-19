package com.sanshuiyuan.asset.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateOrderRequest(
    @NotNull Long skuId,
    @NotNull @Min(1) Integer qty,
    @NotBlank String address
) {}
