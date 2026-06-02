package com.sanshuiyuan.cend.wx.api.dto;

/**
 * 微信 JS-SDK {@code wx.config} 鉴权参数（009 T9.3）。
 *
 * @param appId     公众号 appId。
 * @param timestamp 生成签名的秒级时间戳（字符串，前端原样回填 wx.config）。
 * @param nonceStr  随机串。
 * @param signature jsapi_ticket+noncestr+timestamp+url 的 SHA1 签名；为空表示未配置真实凭证，前端跳过 wx.config。
 */
public record JsSdkSignatureResponse(String appId, String timestamp, String nonceStr, String signature) {}
