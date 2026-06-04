package com.sanshuiyuan.cend.api.dto;

import java.util.List;

/**
 * GET /api/c/landing/config 的 data 体（plan §5.1 契约）。
 */
public record LandingConfigResponse(
        int version,
        HeroDto hero,
        List<FeatureDto> features,
        SimulatorDto simulator,
        List<TrustBadgeDto> trustBadges,
        FooterDto footer
) {
}
