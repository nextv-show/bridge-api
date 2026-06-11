package com.sanshuiyuan.cend.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 桩实现：小程序 app-secret 未配置（本地/测试）时使用，永远返回 null（手机号核验不可用，调用方降级为"未关联"）。
 */
public class StubWxMiniPhoneClient implements WxMiniPhoneClient {

    private static final Logger log = LoggerFactory.getLogger(StubWxMiniPhoneClient.class);

    @Override
    public String getPhoneNumber(String code) {
        log.info("[stub] 小程序手机号核验未配置，返回 null");
        return null;
    }
}
