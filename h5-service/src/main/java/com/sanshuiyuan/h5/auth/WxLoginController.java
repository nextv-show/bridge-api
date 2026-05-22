package com.sanshuiyuan.h5.auth;

import com.sanshuiyuan.h5.common.ApiResponse;
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

    public WxLoginController(WxAuthClient wxAuthClient, H5JwtService jwtService) {
        this.wxAuthClient = wxAuthClient;
        this.jwtService = jwtService;
    }

    @PostMapping("/wx-login")
    public ApiResponse<WxLoginResponse> wxLogin(@Valid @RequestBody WxLoginRequest req) {
        String openid = wxAuthClient.code2openid(req.code());
        String token = jwtService.generate(openid);
        return ApiResponse.ok(new WxLoginResponse(token));
    }

    public record WxLoginRequest(@NotBlank String code) {}

    public record WxLoginResponse(String token) {}
}
