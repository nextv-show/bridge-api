package com.sanshuiyuan.h5.auth;

import com.sanshuiyuan.h5.common.ApiResponse;
import com.sanshuiyuan.h5.referral.ReferralBindingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * 微信网页授权静默登录：H5 在微信内拿到 code 后调本接口换取 H5 会话 JWT。
 * 后续受保护接口（KYC / 下单 / 支付）携带该 token，后端从中解析 openid。
 */
@RestController
@RequestMapping("/api/h5/auth")
@Tag(name = "Auth")
public class WxLoginController {

    private final WxAuthClient wxAuthClient;
    private final H5JwtService jwtService;
    private final ReferralBindingService referralBindingService;

    public WxLoginController(WxAuthClient wxAuthClient, H5JwtService jwtService,
                             ReferralBindingService referralBindingService) {
        this.wxAuthClient = wxAuthClient;
        this.jwtService = jwtService;
        this.referralBindingService = referralBindingService;
    }

    @PostMapping("/wx-login")
    public ApiResponse<WxLoginResponse> wxLogin(@Valid @RequestBody WxLoginRequest req) {
        String openid = wxAuthClient.code2openid(req.code());
        // 定位/创建本人；首次注册时用 refId 建立 L1/L2 关系链（解码失败/自我邀请/已注册均降级，绝不阻断登录）。
        referralBindingService.onWxLogin(openid, req.refId());
        String token = jwtService.generate(openid);
        return ApiResponse.ok(new WxLoginResponse(token));
    }

    /**
     * @param code  微信网页授权 code（必填）。
     * @param refId 推广 ref_id（可选，推广者 user_id 的 HMAC 签名形式）。仅在首次注册时用于建立 L1/L2 关系链；
     *              已注册用户携带亦不改变关系链。解码失败按自然流量处理，不阻断登录（绑定逻辑见 008b）。
     */
    public record WxLoginRequest(@NotBlank String code, String refId) {}

    public record WxLoginResponse(String token) {}
}
