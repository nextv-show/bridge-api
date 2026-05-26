package com.sanshuiyuan.h5.auth;

import com.sanshuiyuan.h5.common.ApiResponse;
import com.sanshuiyuan.h5.infra.client.UserServiceClient;
import com.sanshuiyuan.h5.referral.InvalidRefIdException;
import com.sanshuiyuan.h5.referral.RefIdCodec;
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
    private final UserServiceClient userServiceClient;
    private final RefIdCodec refIdCodec;

    public WxLoginController(WxAuthClient wxAuthClient, H5JwtService jwtService,
                             ReferralBindingService referralBindingService,
                             UserServiceClient userServiceClient, RefIdCodec refIdCodec) {
        this.wxAuthClient = wxAuthClient;
        this.jwtService = jwtService;
        this.referralBindingService = referralBindingService;
        this.userServiceClient = userServiceClient;
        this.refIdCodec = refIdCodec;
    }

    @PostMapping("/wx-login")
    public ApiResponse<WxLoginResponse> wxLogin(@Valid @RequestBody WxLoginRequest req) {
        String openid = wxAuthClient.code2openid(req.code());
        // 仅定位/创建本人 + 写入微信昵称/头像资料快照（014）；L1/L2 关系链改由用户落地页显式确认后经
        // confirm-binding 绑定。
        referralBindingService.onWxLogin(openid, req.nickname(), req.avatarUrl());
        String token = jwtService.generate(openid);
        // spec 012: 并入统一用户体系。inviterId 由 RefIdCodec 解密后下传（解码失败按自然流量）。
        // H5 网页授权仅得 openid，unionid 暂为 null（user-service 按 openid 查/建）。
        userServiceClient.syncH5(openid, null, decodeInviterIdQuietly(req.refId()));
        return ApiResponse.ok(new WxLoginResponse(token));
    }

    /** 解密 refId 为推广者 user_id；空/解码失败均返回 null（按自然流量，绝不阻断登录）。 */
    private Long decodeInviterIdQuietly(String refId) {
        if (refId == null || refId.isBlank()) {
            return null;
        }
        try {
            return refIdCodec.decode(refId);
        } catch (InvalidRefIdException e) {
            return null;
        }
    }

    /**
     * @param code      微信网页授权 code（必填）。
     * @param refId     推广 ref_id（可选，推广者 user_id 的 HMAC 签名形式）。绑定改由 confirm-binding 显式触发；
     *                  此处仅用于统一用户体系同步（syncH5），解码失败按自然流量，不阻断登录。
     * @param nickname  微信昵称（可选）。前端经微信授权获取后回传，登录时写入资料快照（014）。
     * @param avatarUrl 微信头像 URL（可选）。前端经微信授权获取后回传，登录时写入资料快照（014）。
     */
    public record WxLoginRequest(@NotBlank String code, String refId, String nickname, String avatarUrl) {}

    public record WxLoginResponse(String token) {}
}
