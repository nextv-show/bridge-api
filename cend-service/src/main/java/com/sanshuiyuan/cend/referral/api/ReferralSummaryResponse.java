package com.sanshuiyuan.cend.referral.api;

/**
 * 我的推荐汇总（015）。<b>零层级字段</b>：仅暴露聚合计数，不含任何 inviter_id / grand_inviter_id / L1 / L2。
 *
 * @param totalCount      直接推荐人总数（L1）
 * @param registeredCount 已注册未购买人数
 * @param purchasedCount  已购买人数
 */
public record ReferralSummaryResponse(int totalCount, int registeredCount, int purchasedCount) {}
