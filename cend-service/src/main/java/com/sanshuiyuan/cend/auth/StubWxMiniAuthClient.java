package com.sanshuiyuan.cend.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 本地/未配置小程序凭证时的占位实现：用 jsCode 派生稳定 openid，便于联调，不打真实微信。
 * 仅在 wx.miniprogram.app-secret 未配置（dev/test）时生效。
 *
 * <p>桩场景下 unionid 返回 null，统一身份回退到派生 openid（不影响本地联调）。
 */
public class StubWxMiniAuthClient implements WxMiniAuthClient {

    private static final Logger log = LoggerFactory.getLogger(StubWxMiniAuthClient.class);

    @Override
    public MiniSession code2session(String jsCode) {
        String openid = "dev-mp-openid-" + Integer.toHexString(jsCode == null ? 0 : jsCode.hashCode());
        log.info("[stub] wx jscode2session -> 派生本地小程序 openid");
        return new MiniSession(openid, null, "dev-session-key");
    }
}
