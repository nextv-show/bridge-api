package com.sanshuiyuan.h5.wx.api;

import com.sanshuiyuan.h5.common.ApiResponse;
import com.sanshuiyuan.h5.wx.WxJsSdkService;
import com.sanshuiyuan.h5.wx.api.dto.JsSdkSignatureResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 微信 JS-SDK 鉴权接口（009 T9.3）。公开（无需 H5 JWT），供 H5 在微信内初始化 {@code wx.config}。
 */
@RestController
@RequestMapping("/api/h5/wx")
@Tag(name = "WeChat JS-SDK", description = "微信 JS-SDK 签名 / 分享配置")
public class WxJsSdkController {

    private final WxJsSdkService jsSdkService;

    public WxJsSdkController(WxJsSdkService jsSdkService) {
        this.jsSdkService = jsSdkService;
    }

    @Operation(summary = "获取 JS-SDK 签名",
            description = "传入当前页面去 #hash 的完整 URL，返回 appId/timestamp/nonceStr/signature。"
                    + "signature 为空表示后端未配置真实公众号凭证，前端应跳过 wx.config。")
    @GetMapping("/jssdk/signature")
    public ApiResponse<JsSdkSignatureResponse> signature(@RequestParam("url") String url) {
        return ApiResponse.ok(jsSdkService.sign(url));
    }
}
