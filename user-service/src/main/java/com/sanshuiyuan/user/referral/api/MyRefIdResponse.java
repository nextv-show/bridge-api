package com.sanshuiyuan.user.referral.api;

/**
 * 当前登录用户的推广加密 ref_id（009 T9.5）。
 *
 * @param refId    当前用户 user_id 经 {@link com.sanshuiyuan.user.referral.RefIdCodec} 加密的防篡改串。
 * @param shareUrl 可直接用于分享的完整链接（linkBase + "/?ref_id=" + refId）；小程序分享无需 url，
 *                 linkBase 配置留空时仅返回相对路径（前端可忽略）。
 */
public record MyRefIdResponse(String refId, String shareUrl) {}
