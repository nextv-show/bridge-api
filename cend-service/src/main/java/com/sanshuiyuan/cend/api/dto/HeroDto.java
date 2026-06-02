package com.sanshuiyuan.cend.api.dto;

import java.util.List;

/**
 * Hero 区：横版 logo + 主标题（多行）+ 副标题 + 3 个 KPI + 行业 chip。
 */
public record HeroDto(
        String logoUrl,
        List<String> titleLines,
        String subtitle,
        List<KpiDto> kpis,
        List<String> industries
) {
    public record KpiDto(String label, String value, String unit) {
    }
}
