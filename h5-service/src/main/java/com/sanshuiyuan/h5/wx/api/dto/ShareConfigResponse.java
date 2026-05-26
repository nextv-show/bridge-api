package com.sanshuiyuan.h5.wx.api.dto;

/**
 * 微信分享卡片配置（009 T9.5）。前端取此填充 updateAppMessageShareData / updateTimelineShareData。
 *
 * @param title    分享标题（后端下发，合规）。
 * @param desc     分享描述（后端下发，合规）。
 * @param imgUrl   分享缩略图绝对 URL（可空，前端兜底品牌 logo）。
 * @param linkBase 分享链接基址（H5 公网根，如 https://h5.sanshuiyuan.com）；前端拼 ?ref_id=<加密ID> 得最终 link。
 */
public record ShareConfigResponse(String title, String desc, String imgUrl, String linkBase) {}
