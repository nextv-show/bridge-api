package com.sanshuiyuan.cend.api.dto;

/**
 * 服务返利测算器参数（决策门 G-5：沿用 home.jsx 分段模型，系数以基点 bp 下发避免浮点漂移）。
 * 月/年返点 = 基础水务返利 + 网络运营分润：
 *   每升每日返利（元）= baseRateBp / 10000（如 850 → 0.085 元/L）
 *   网络运营分润：日均 > bonusThresholdLiters 的部分按 networkBonusBp / 10000（如 400 → 0.04 元/L）
 *   预估年度 = round((daily + networkBonus) * 365)
 * 前端按此公式即时计算（无 debounce），业务系数全部由后端下发，前端仅保留兜底默认。
 */
public record SimulatorDto(
        int minLiters,
        int maxLiters,
        int defaultLiters,
        int baseRateBp,
        int networkBonusBp,
        int bonusThresholdLiters,
        String unit,
        String outputLabel,
        String disclaimer
) {
}
