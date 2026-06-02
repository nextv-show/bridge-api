package com.sanshuiyuan.cend.auth;

import com.sanshuiyuan.cend.common.ApiResponse;
import com.sanshuiyuan.cend.infra.client.UserServiceClient;
import com.sanshuiyuan.cend.referral.InvalidRefIdException;
import com.sanshuiyuan.cend.referral.RefIdCodec;
import com.sanshuiyuan.cend.referral.ReferralBindingService;
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
    private final WxMiniAuthClient wxMiniAuthClient;
    private final H5JwtService jwtService;
    private final ReferralBindingService referralBindingService;
    private final UserServiceClient userServiceClient;
    private final RefIdCodec refIdCodec;

    public WxLoginController(WxAuthClient wxAuthClient, WxMiniAuthClient wxMiniAuthClient,
                             H5JwtService jwtService,
                             ReferralBindingService referralBindingService,
                             UserServiceClient userServiceClient, RefIdCodec refIdCodec) {
        this.wxAuthClient = wxAuthClient;
        this.wxMiniAuthClient = wxMiniAuthClient;
        this.jwtService = jwtService;
        this.referralBindingService = referralBindingService;
        this.userServiceClient = userServiceClient;
        this.refIdCodec = refIdCodec;
    }

    @PostMapping("/wx-login")
    public ApiResponse<WxLoginResponse> wxLogin(@Valid @RequestBody WxLoginRequest req) {
        WxAuthClient.WxIdentity id = wxAuthClient.code2identity(req.code());
        // 统一身份：unionid 优先（公众号绑定开放平台时返回），否则回退公众号 openid（保持既有行为）。
        String canonicalId = canonical(id.unionid(), id.openid());
        // 仅定位/创建本人 + 写入微信昵称/头像资料快照（014）；L1/L2 关系链改由用户落地页显式确认后经
        // confirm-binding 绑定。
        referralBindingService.onWxLogin(canonicalId, req.nickname(), req.avatarUrl());
        // 公众号网页支付的 payer 仍是公众号 openid（subject 可能已是 unionid，不能直接当 payer）。
        String token = jwtService.generate(canonicalId, id.openid(), "H5");
        // spec 012: 并入统一用户体系。inviterId 由 RefIdCodec 解密后下传（解码失败按自然流量）。
        userServiceClient.syncH5(canonicalId, id.unionid(), decodeInviterIdQuietly(req.refId()));
        return ApiResponse.ok(new WxLoginResponse(token));
    }

    /**
     * 小程序登录：前端 {@code wx.login()} 拿到 jsCode 后调本接口换取 H5 会话 JWT。
     * 统一身份按 unionid（拿不到回退小程序 openid）；mp_openid（预支付 payer）与 clientType=MINI 写入 token。
     */
    @PostMapping("/wx-mini-login")
    public ApiResponse<WxLoginResponse> wxMiniLogin(@Valid @RequestBody WxMiniLoginRequest req) {
        WxMiniAuthClient.MiniSession s = wxMiniAuthClient.code2session(req.jsCode());
        String canonicalId = canonical(s.unionid(), s.openid());
        referralBindingService.onWxLogin(canonicalId, req.nickname(), req.avatarUrl());
        String token = jwtService.generate(canonicalId, s.openid(), "MINI");
        userServiceClient.syncH5(canonicalId, s.unionid(), decodeInviterIdQuietly(req.refId()));
        return ApiResponse.ok(new WxLoginResponse(token));
    }

    private static String canonical(String unionid, String fallbackOpenid) {
        return (unionid != null && !unionid.isBlank()) ? unionid : fallbackOpenid;
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

    /**
     * @param jsCode    {@code wx.login()} 返回的临时登录凭证（必填）。
     * @param refId     推广 ref_id（可选，同 {@link WxLoginRequest#refId()}）。
     * @param nickname  微信昵称（可选）。
     * @param avatarUrl 微信头像 URL（可选）。
     */
    public record WxMiniLoginRequest(@NotBlank String jsCode, String refId, String nickname, String avatarUrl) {}

    public record WxLoginResponse(String token) {}
}
