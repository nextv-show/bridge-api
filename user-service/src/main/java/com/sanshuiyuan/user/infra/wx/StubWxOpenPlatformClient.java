package com.sanshuiyuan.user.infra.wx;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * dev profile 微信开放平台（App 登录）桩：按 code 派生确定性 unionid/openid。
 * 与 {@link StubWxMiniProgramClient} 同一套 unionid 规则（dev-union-{code}），
 * 因此同一标识在小程序端与 App 端会落到同一用户，便于验证两端共账户。
 *
 * <p>仅在 `dev` profile 激活；生产用 {@link WxOpenPlatformClient}（@Profile("!dev")）。
 */
@Component
@Profile("dev")
public class StubWxOpenPlatformClient extends WxOpenPlatformClient {

    public StubWxOpenPlatformClient(RestTemplate restTemplate) {
        super(restTemplate, "stub-op-appid", "stub-op-secret");
    }

    @Override
    public WxOAuthResponse exchangeAppCode(String wxAuthCode) {
        return new WxOAuthResponse(
                "dev-app-openid-" + wxAuthCode,
                "dev-union-" + wxAuthCode,
                "dev-access-token");
    }
}
