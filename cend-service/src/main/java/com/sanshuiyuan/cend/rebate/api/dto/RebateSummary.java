package com.sanshuiyuan.cend.rebate.api.dto;

/**
 * 返利摘要（对外视图）。
 *
 * <p><b>合规：</b>{@code confirmedTotalCents} 仅累加 CONFIRMED（已确认）记录——冷静期中的 FROZEN 金额绝不计入，
 * 故摘要总额本身即不泄露任何冻结中金额。
 *
 * @param confirmedTotalCents 已确认返利总额（分），仅 CONFIRMED 计入
 * @param frozenCount         冻结中（FROZEN）笔数
 * @param cancelledCount      已取消（CANCELLED）笔数
 */
public record RebateSummary(
        long confirmedTotalCents,
        long frozenCount,
        long cancelledCount
) {
    public static RebateSummary empty() {
        return new RebateSummary(0L, 0L, 0L);
    }
}
