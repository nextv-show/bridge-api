package com.sanshuiyuan.matching.request.domain;

/**
 * 期望价格档位，有序：T_040 &lt; T_080 &lt; T_120 &lt; T_150。
 * {@code order} 提供显式可比序（不依赖声明顺序的 ordinal，但二者一致）。
 */
public enum PriceTier {
    T_040(40),
    T_080(80),
    T_120(120),
    T_150(150);

    private final int order;

    PriceTier(int order) {
        this.order = order;
    }

    public int order() {
        return order;
    }

    /** 期望水费（元/升）：order/100，即 T_040→0.40 … T_150→1.50。 */
    public java.math.BigDecimal yuanPerLiter() {
        return java.math.BigDecimal.valueOf(order).movePointLeft(2);
    }

    /** this 是否 &gt;= other（按价格档位序）。 */
    public boolean atLeast(PriceTier other) {
        return this.order >= other.order;
    }
}
