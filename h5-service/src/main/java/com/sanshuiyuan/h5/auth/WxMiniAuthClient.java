package com.sanshuiyuan.h5.auth;

/**
 * 微信小程序登录：用前端 {@code wx.login()} 拿到的 jsCode 调 jscode2session 换取 openid + unionid。
 * 与公众号网页授权（{@link WxAuthClient}）分属不同 appId/secret，故独立成接口。
 */
public interface WxMiniAuthClient {

    /** jsCode → 小程序会话；失败抛 {@link com.sanshuiyuan.h5.common.BizException}。 */
    MiniSession code2session(String jsCode);

    /**
     * @param openid     小程序 openid（预支付 payer）。
     * @param unionid    开放平台 unionid（绑定时返回，否则为 null）。
     * @param sessionKey 会话密钥（当前流程未使用，保留）。
     */
    record MiniSession(String openid, String unionid, String sessionKey) {}
}
