package com.sanshuiyuan.cend.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 本地/未配置微信凭证时的占位实现：用 code 派生稳定 openid，便于联调，不打真实微信。
 * 仅在 wxpay.app-secret 未配置（dev/test）时生效。
 */
public class StubWxAuthClient implements WxAuthClient {

    private static final Logger log = LoggerFactory.getLogger(StubWxAuthClient.class);

    @Override
    public String code2openid(String code) {
        String openid = "dev-openid-" + Integer.toHexString(code == null ? 0 : code.hashCode());
        log.info("[stub] wx code2openid -> 派生本地 openid");
        return openid;
    }
}
