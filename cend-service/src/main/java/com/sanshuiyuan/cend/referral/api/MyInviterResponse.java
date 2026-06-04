package com.sanshuiyuan.cend.referral.api;

/**
 * 当前用户的邀请人脱敏资料（F16 邀请人状态条）。
 *
 * @param nicknameMasked 邀请人脱敏昵称
 * @param avatarUrl      邀请人头像 URL
 * @param boundAt        绑定时间（yyyy-MM-dd）
 */
public record MyInviterResponse(String nicknameMasked, String avatarUrl, String boundAt) {}
