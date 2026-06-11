package com.sanshuiyuan.cend.identity;

/**
 * 微信小程序"手机号快速验证"：用 getPhoneNumber 按钮回传的 {@code code} 换取微信级已验证手机号。
 */
public interface WxMiniPhoneClient {

    /**
     * @param code 小程序 {@code <button open-type="getPhoneNumber">} 回调的动态令牌。
     * @return 纯手机号（11 位，国内）；获取失败/未配置时返回 {@code null}（调用方降级处理，不阻断）。
     */
    String getPhoneNumber(String code);
}
