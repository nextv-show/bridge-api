package com.sanshuiyuan.h5.api.dto;

/**
 * 区块01 FeatureCard：iconKey 由前端映射内联 SVG。
 */
public record FeatureDto(
        Long id,
        String iconKey,
        String title,
        String subtitle,
        String description,
        int sortOrder
) {
}
