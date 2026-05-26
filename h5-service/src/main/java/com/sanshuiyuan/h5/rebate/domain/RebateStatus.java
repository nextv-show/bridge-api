package com.sanshuiyuan.h5.rebate.domain;

/**
 * 返利状态机。<b>单向流转，无逆向</b>：
 * <pre>
 *   FROZEN ──(冷静期满 24h)──▶ CONFIRMED
 *   FROZEN ──(冷静期内退款)──▶ CANCELLED
 *   CONFIRMED ──(冷静期后退款)──▶ CANCELLED
 * </pre>
 * 合规：FROZEN 期间不对外展示具体金额，仅 CONFIRMED 方可展示。
 */
public enum RebateStatus {
    FROZEN, CONFIRMED, CANCELLED
}
