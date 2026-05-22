package com.sanshuiyuan.h5.auth;

/**
 * 微信网页授权（公众号 snsapi_base）：用前端回传的 code 换 openid。
 */
public interface WxAuthClient {

    /** code → openid；失败抛 {@link com.sanshuiyuan.h5.common.BizException}。 */
    String code2openid(String code);
}
