package com.sanshuiyuan.cend.wx.api;

import com.sanshuiyuan.cend.common.ApiResponse;
import com.sanshuiyuan.cend.wx.WxJsSdkService;
import com.sanshuiyuan.cend.wx.WxShareProperties;
import com.sanshuiyuan.cend.wx.api.dto.JsSdkSignatureResponse;
import com.sanshuiyuan.cend.wx.api.dto.ShareConfigResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 微信 JS-SDK 鉴权 + 分享配置接口（009 T9.3 / T9.5）。公开（无需 H5 JWT），供 H5 在微信内初始化
 * {@code wx.config} 与读取合规分享文案。
 */
@RestController
@RequestMapping("/api/h5/wx")
@Tag(name = "WeChat JS-SDK", description = "微信 JS-SDK 签名 / 分享配置")
public class WxJsSdkController {

    private final WxJsSdkService jsSdkService;
    private final WxShareProperties shareProperties;
    private final String linkBase;

    public WxJsSdkController(WxJsSdkService jsSdkService,
                             WxShareProperties shareProperties,
                             @Value("${h5.public-base-url:}") String publicBaseUrl) {
        this.jsSdkService = jsSdkService;
        this.shareProperties = shareProperties;
        // 去掉末尾斜杠，前端统一以 linkBase + "/?ref_id=" 拼接。
        this.linkBase = publicBaseUrl == null ? "" : publicBaseUrl.replaceAll("/+$", "");
    }

    @Operation(summary = "获取 JS-SDK 签名",
            description = "传入当前页面去 #hash 的完整 URL，返回 appId/timestamp/nonceStr/signature。"
                    + "signature 为空表示后端未配置真实公众号凭证，前端应跳过 wx.config。")
    @GetMapping("/jssdk/signature")
    public ApiResponse<JsSdkSignatureResponse> signature(@RequestParam("url") String url) {
        return ApiResponse.ok(jsSdkService.sign(url));
    }

    @Operation(summary = "获取微信分享卡片文案",
            description = "返回合规的 title/desc/imgUrl + 分享链接基址 linkBase；前端不硬编码文案。")
    @GetMapping("/share-config")
    public ApiResponse<ShareConfigResponse> shareConfig() {
        return ApiResponse.ok(new ShareConfigResponse(
                shareProperties.getTitle(),
                shareProperties.getDesc(),
                shareProperties.getImgUrl(),
                linkBase));
    }
}
