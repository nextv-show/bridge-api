package com.sanshuiyuan.cend.referral;

/**
 * 微信小程序码生成 —— 调用 wxacode.getUnlimited 返回 Base64 data URL。
 */
public interface WxMiniCodeClient {

    /**
     * 获取无限量小程序码（JPEG），返回 data URL。
     *
     * @param scene      场景值（最大 32 字符），前端扫码进入小程序时可在 onLaunch/onShow 的 query.scene 中获取
     * @param page       小程序页面路径（如 "pages/index/index"），必须是对应版本已存在的小程序页面
     * @param envVersion 要打开的小程序版本：release=正式版 / trial=体验版 / develop=开发版；
     *                   必须与实际已上传/发布的版本匹配，否则微信侧报错。
     * @return data:image/jpeg;base64,... 格式的图片数据 URL
     */
    String getUnlimitedWxaCode(String scene, String page, String envVersion);
}
