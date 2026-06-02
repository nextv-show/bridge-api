package com.sanshuiyuan.cend.api.dto;

/**
 * 区块03 TrustBadge。
 */
public record TrustBadgeDto(
        Long id,
        String iconKey,
        String title,
        String subtitle,
        int sortOrder
) {
}
