package com.sanshuiyuan.h5.checkout.infra.minikyc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 本地/未配置真实人脸核身凭证时的占位实现：派生稳定 certifyId，queryResult 恒通过，便于全链路联调。
 * 仅在 tencent.faceid.secret-id 未配置（dev/test/未对接）时生效。
 */
public class StubMiniKycProvider implements MiniKycProvider {

    private static final Logger log = LoggerFactory.getLogger(StubMiniKycProvider.class);

    @Override
    public MiniKycInitResult init(String openid, String realName, String idNo) {
        String certifyId = "dev-mini-certify-" + Integer.toHexString((openid + ":" + idNo).hashCode());
        log.info("[stub] 小程序人脸核身 init -> certifyId={}", certifyId);
        return new MiniKycInitResult(certifyId, Map.of("provider", "stub", "certifyId", certifyId));
    }

    @Override
    public MiniKycResult queryResult(String certifyId) {
        log.info("[stub] 小程序人脸核身 queryResult -> PASS certifyId={}", certifyId);
        return new MiniKycResult(true);
    }
}
