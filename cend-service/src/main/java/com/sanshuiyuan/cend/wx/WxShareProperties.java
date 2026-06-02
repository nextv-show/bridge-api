package com.sanshuiyuan.cend.wx;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信分享卡片文案配置（009 T9.5）。文案由后端下发、前端不硬编码，便于随时合规调整。
 *
 * <p><b>合规铁律</b>：title/desc 严禁出现「分红/投资回报/保本保息/年化收益/理财」等金融化词汇；
 * 默认值即合规兜底文案，运营如需调整经配置覆盖，不改前端。
 */
@Component
@ConfigurationProperties(prefix = "wx.share")
public class WxShareProperties {

    /** 分享标题（合规兜底）。 */
    private String title = "三水元 · AI 健康饮水";

    /** 分享描述（合规兜底）。 */
    private String desc = "开启 AI 健康饮水档案";

    /** 分享缩略图绝对 URL；为空时前端用品牌默认 logo 兜底。 */
    private String imgUrl = "";

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDesc() { return desc; }
    public void setDesc(String desc) { this.desc = desc; }

    public String getImgUrl() { return imgUrl; }
    public void setImgUrl(String imgUrl) { this.imgUrl = imgUrl; }
}
