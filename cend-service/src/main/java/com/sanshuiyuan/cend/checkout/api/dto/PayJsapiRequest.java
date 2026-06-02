package com.sanshuiyuan.cend.checkout.api.dto;

import jakarta.validation.constraints.NotNull;

public record PayJsapiRequest(
    @NotNull Long orderId
) {}
