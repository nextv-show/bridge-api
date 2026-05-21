package com.sanshuiyuan.admin.api.dto;

public record SkuRequest(String name, Long priceCents, Long depositCents,
                         String benefitsMd, String imageUrl) {}
