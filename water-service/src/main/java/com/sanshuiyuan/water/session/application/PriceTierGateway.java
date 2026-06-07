package com.sanshuiyuan.water.session.application;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 单价网关（V1 stub）。按价格档位返回每升单价（分）。
 * 后续对接 matching-service S2S：反查 matching_assignments → expected_price_tier → 映射。
 */
@Component
public class PriceTierGateway {

    // V1: 硬编码常量映射。后续对接 matching-service S2S 接口
    private static final Map<String, Integer> TIER_PRICE_MAP = Map.of(
        "T_040", 40, "T_080", 80, "T_120", 120, "T_150", 150
    );
    private static final int DEFAULT_PRICE_PER_LITER_CENTS = 80; // T_080 默认

    public int getPricePerLiterCents(String sn) {
        // TODO: S2S 反查 matching_assignments → expected_price_tier → 映射
        return DEFAULT_PRICE_PER_LITER_CENTS;
    }
}
