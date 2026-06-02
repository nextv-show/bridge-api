package com.sanshuiyuan.cend.referral.api;

import java.util.List;

/**
 * GET /api/h5/referral/my-referrals 返回体（015）。
 *
 * @param summary 推荐汇总计数
 * @param items   推荐记录列表（已按 status 过滤）
 */
public record MyReferralsResponse(ReferralSummaryResponse summary, List<ReferralItemResponse> items) {}
