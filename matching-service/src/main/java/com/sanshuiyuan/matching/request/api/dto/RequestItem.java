package com.sanshuiyuan.matching.request.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 列表/详情项。contact_phone 按调用方权限明文或脱敏；distance_km 仅 nearby/列表带值。 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record RequestItem(
        @JsonProperty("id") Long id,
        @JsonProperty("contact_name") String contactName,
        @JsonProperty("contact_phone") String contactPhone,
        @JsonProperty("address") String address,
        @JsonProperty("lat") BigDecimal lat,
        @JsonProperty("lng") BigDecimal lng,
        @JsonProperty("scene_type") String sceneType,
        @JsonProperty("est_daily_liters") Integer estDailyLiters,
        @JsonProperty("expected_price_tier") String expectedPriceTier,
        @JsonProperty("status") String status,
        @JsonProperty("distance_km") Double distanceKm,
        @JsonProperty("locked_at") LocalDateTime lockedAt,
        @JsonProperty("created_at") LocalDateTime createdAt
) {}
