package com.sanshuiyuan.matching.request.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * POST /matching/requests 请求体（snake_case 线格式，显式 @JsonProperty 映射）。
 * scene_type / expected_price_tier 用 String 接收，在 UseCase 做严格枚举校验以返回 422+code。
 */
public record CreateRequestBody(
        @JsonProperty("contact_name") @NotBlank @Size(max = 64) String contactName,
        @JsonProperty("contact_phone") @NotBlank String contactPhone,
        @JsonProperty("address") @NotBlank @Size(max = 255) String address,
        @JsonProperty("lat") @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal lat,
        @JsonProperty("lng") @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal lng,
        @JsonProperty("scene_type") @NotBlank String sceneType,
        @JsonProperty("est_daily_liters") @NotNull @Positive Integer estDailyLiters,
        @JsonProperty("expected_price_tier") @NotBlank String expectedPriceTier
) {}
