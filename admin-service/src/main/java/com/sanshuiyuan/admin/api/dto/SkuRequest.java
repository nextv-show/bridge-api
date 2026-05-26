package com.sanshuiyuan.admin.api.dto;

import java.math.BigDecimal;

public record SkuRequest(
    String name,
    String code,
    String subtitle,
    String category,
    Long priceCents,
    Long originalCents,
    Long costCents,
    Integer stock,
    Integer stockWarn,
    Integer s1Months,
    Integer s2Months,
    Integer fuseAt,
    Integer annualizedBp,
    String accent,
    Boolean featured,
    String note,
    String status,
    // Legacy fields kept for backward compat
    Long depositCents,
    String benefitsMd,
    String imageUrl
) {}
