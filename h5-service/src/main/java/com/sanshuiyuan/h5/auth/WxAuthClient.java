package com.sanshuiyuan.h5.auth;

/**
 * 微信网页授权（公众号 snsapi_base）：用前端回传的 code 换身份。
 */
public interface WxAuthClient {

    /** code → openid；失败抛 {@link com.sanshuiyuan.h5.common.BizException}。 */
    String code2openid(String code);

    /**
     * code → 身份（openid + unionid）。unionid 仅在公众号与开放平台绑定时返回，否则为 null。
     * 默认实现回退到仅 openid（unionid=null），便于桩/旧实现。
     */
    default WxIdentity code2identity(String code) {
        return new WxIdentity(code2openid(code), null);
    }

    /** @param openid 公众号 openid。 @param unionid 开放平台 unionid（可能为 null）。 */
    record WxIdentity(String openid, String unionid) {}
}
