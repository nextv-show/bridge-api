package com.sanshuiyuan.cend.referral.api;

/**
 * 当前用户的邀请人脱敏资料（F16 邀请人状态条）。
 *
 * @param nicknameMasked 邀请人脱敏昵称；微信未授权时为空串
 * @param displayName    可识别展示名：微信昵称脱敏 &gt; 实名脱敏 &gt; 手机尾号；均缺失为空串（前端兜底「微信用户」）
 * @param avatarUrl      邀请人头像 URL
 * @param boundAt        绑定时间（yyyy-MM-dd）
 */
public record MyInviterResponse(String nicknameMasked, String displayName, String avatarUrl, String boundAt) {}
