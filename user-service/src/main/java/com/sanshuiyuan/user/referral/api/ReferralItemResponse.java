package com.sanshuiyuan.user.referral.api;

/**
 * 单条推荐记录（015）。<b>零层级字段</b>：不含 inviter_id / grand_inviter_id / level / L1 / L2。
 *
 * @param userId         被推荐人 user_id，仅供前端列表 key 使用（非展示用，亦不可定位他人 PII）
 * @param nicknameMasked 脱敏昵称（首字 * 尾字，规则同 014）
 * @param avatarUrl      微信头像 URL（非可定位 PII）
 * @param registeredAt   注册日期 {@code yyyy-MM-dd}
 * @param status         {@code REGISTERED}（未购买）/ {@code PURCHASED}（已购买）
 * @param purchasedAt    最近购买日期 {@code yyyy-MM-dd}；未购买为 {@code null}
 */
public record ReferralItemResponse(
        long userId,
        String nicknameMasked,
        String avatarUrl,
        String registeredAt,
        String status,
        String purchasedAt) {}
