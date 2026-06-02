package com.sanshuiyuan.cend.referral.api;

/**
 * 当前登录用户的推广加密 ref_id（009 T9.5）。
 *
 * @param refId    当前用户 H5 user_id 经 {@link com.sanshuiyuan.cend.referral.RefIdCodec} 加密的防篡改串。
 * @param shareUrl 可直接用于分享的完整链接（linkBase + "/?ref_id=" + refId）；linkBase 为空时仅返回相对路径。
 */
public record MyRefIdResponse(String refId, String shareUrl) {}
