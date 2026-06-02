package com.sanshuiyuan.cend.checkout.api.dto;

import jakarta.validation.constraints.NotBlank;

public record KycVerifyRequest(
    @NotBlank String certifyId
) {}
