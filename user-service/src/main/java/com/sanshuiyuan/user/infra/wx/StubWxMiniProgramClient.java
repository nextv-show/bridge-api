package com.sanshuiyuan.user.infra.wx;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * dev profile 微信小程序登录桩：不调真实微信，直接按 jsCode 派生确定性的 unionid/openid，
 * 供 e2e 跑通「登录 → JWT → 业务」全链路。不同 jsCode → 不同用户，便于模拟多账号。
 *
 * <p>仅在 `dev` profile 激活；生产用 {@link WxMiniProgramClient}（@Profile("!dev")）。
 */
@Component
@Profile("dev")
public class StubWxMiniProgramClient extends WxMiniProgramClient {

    public StubWxMiniProgramClient(RestTemplate restTemplate) {
        super(restTemplate, "stub-mp-appid", "stub-mp-secret");
    }

    @Override
    public WxSessionResponse code2session(String jsCode) {
        return new WxSessionResponse(
                "dev-mp-openid-" + jsCode,
                "dev-union-" + jsCode,
                "dev-session-key");
    }
}
