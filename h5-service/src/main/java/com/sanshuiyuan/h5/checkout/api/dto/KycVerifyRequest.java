package com.sanshuiyuan.h5.checkout.api.dto;

import jakarta.validation.constraints.NotBlank;

public record KycVerifyRequest(
    @NotBlank String certifyId
) {}
