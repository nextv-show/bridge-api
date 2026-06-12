package com.sanshuiyuan.matching.request.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 列表/详情项。contact_phone 按调用方权限明文或脱敏；distance_km 仅 nearby/列表带值。
 * est_monthly_gmv 为「预估月流水」毛口径（tier元/升 × 日用水 × 30，design §3）；
 * recommend_reasons 仅 nearby 带值（分桶反推，design §2.4），其余路径为空列表。
 * 注意：撮合「匹配分」仅用于后端排序，**不进 DTO**（design §2.1）。
 */
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
        @JsonProperty("claim_confirmed_at") LocalDateTime claimConfirmedAt,
        @JsonProperty("created_at") LocalDateTime createdAt,
        @JsonProperty("user_id") Long userId,
        @JsonProperty("locked_by_user_id") Long lockedByUserId,
        @JsonProperty("is_owner") boolean isOwner,
        @JsonProperty("is_lock_owner") boolean isLockOwner,
        @JsonProperty("est_monthly_gmv") BigDecimal estMonthlyGmv,
        @JsonProperty("recommend_reasons") List<String> recommendReasons
) {}
