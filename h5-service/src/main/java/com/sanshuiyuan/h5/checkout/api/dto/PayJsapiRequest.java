package com.sanshuiyuan.h5.checkout.api.dto;

import jakarta.validation.constraints.NotNull;

public record PayJsapiRequest(
    @NotNull Long orderId
) {}
