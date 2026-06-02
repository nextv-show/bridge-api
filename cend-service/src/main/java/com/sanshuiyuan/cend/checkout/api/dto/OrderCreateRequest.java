package com.sanshuiyuan.cend.checkout.api.dto;

import jakarta.validation.constraints.NotBlank;

public record OrderCreateRequest(
    @NotBlank String specId,
    @NotBlank String payment
) {}
