package com.sanshuiyuan.cend.referral.api;

import com.sanshuiyuan.cend.auth.CurrentOpenid;
import com.sanshuiyuan.cend.common.ApiResponse;
import com.sanshuiyuan.cend.common.BizException;
import com.sanshuiyuan.cend.common.ErrorCode;
import com.sanshuiyuan.cend.referral.CendUser;
import com.sanshuiyuan.cend.referral.CendUserRepository;
import com.sanshuiyuan.cend.referral.InvalidRefIdException;
import com.sanshuiyuan.cend.referral.NicknameMasker;
import com.sanshuiyuan.cend.referral.RefIdCodec;
import com.sanshuiyuan.cend.referral.ReferralBindingService;
import com.sanshuiyuan.cend.referral.ReferralDisplayNameResolver;
import com.sanshuiyuan.cend.referral.ReferralQueryService;
import com.sanshuiyuan.cend.referral.WxMiniCodeClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 推广关系链查询接口（009 T9.5）。<b>需登录态</b>（区别于公开的 /api/c/wx/**）。
 *
 * <p><b>合规铁律</b>：分享链接携带的是后端加密下发的当前用户 ref_id（RefIdCodec），
 * 前端不自行拼装明文 user_id；本接口只读自身、不暴露关系链层级。
 */
@RestController
@RequestMapping("/api/c/referral")
@Tag(name = "Referral", description = "H5 推广关系链（登录态）")
public class ReferralController {

    private final CendUserRepository userRepo;
    private final RefIdCodec refIdCodec;
    private final ReferralBindingService referralBindingService;
    private final ReferralQueryService referralQueryService;
    private final ReferralDisplayNameResolver displayNameResolver;
    private final WxMiniCodeClient wxMiniCodeClient;
    private final String linkBase;

    public ReferralController(CendUserRepository userRepo,
                              RefIdCodec refIdCodec,
                              ReferralBindingService referralBindingService,
                              ReferralQueryService referralQueryService,
                              ReferralDisplayNameResolver displayNameResolver,
                              WxMiniCodeClient wxMiniCodeClient,
                              @Value("${h5.public-base-url:}") String publicBaseUrl) {
        this.userRepo = userRepo;
        this.refIdCodec = refIdCodec;
        this.referralBindingService = referralBindingService;
        this.referralQueryService = referralQueryService;
        this.displayNameResolver = displayNameResolver;
        this.wxMiniCodeClient = wxMiniCodeClient;
        this.linkBase = publicBaseUrl == null ? "" : publicBaseUrl.replaceAll("/+$", "");
    }

    @Operation(summary = "获取当前用户的加密 ref_id",
            description = "用于生成分享链接 ?ref_id=<加密ID>；ref_id 由后端 HMAC 签名，防篡改、不可猜测他人 id。")
    @GetMapping("/my-ref-id")
    public ApiResponse<MyRefIdResponse> myRefId() {
        String openid = CurrentOpenid.require();
        CendUser user = userRepo.findByOpenid(openid)
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));
        String refId = refIdCodec.encode(user.getId());
        String shareUrl = linkBase.isBlank() ? ("/?ref_id=" + refId) : (linkBase + "/?ref_id=" + refId);
        return ApiResponse.ok(new MyRefIdResponse(refId, shareUrl));
    }

    @Operation(summary = "按 ref_id 解析推荐人脱敏资料",
            description = "邀请确认页公开调用（无需登录）。仅返回脱敏昵称+头像，零可定位 PII；"
                    + "ref_id 解码失败或推荐人不存在时静默返回 null（不报错、不泄露细节）。")
    @GetMapping("/resolve-inviter")
    public ApiResponse<ResolveInviterResponse> resolveInviter(@RequestParam("ref_id") String refId) {
        final long inviterId;
        try {
            inviterId = refIdCodec.decode(refId);
        } catch (InvalidRefIdException e) {
            return ApiResponse.ok(null); // 解码失败：静默，不暴露细节。
        }
        CendUser inviter = userRepo.findById(inviterId).orElse(null);
        if (inviter == null) {
            return ApiResponse.ok(null); // 推荐人不存在：静默。
        }
        return ApiResponse.ok(new ResolveInviterResponse(
                NicknameMasker.mask(inviter.getNickname()), inviter.getAvatarUrl()));
    }

    @Operation(summary = "我的推荐人列表与汇总",
            description = "登录态。返回当前用户直接推荐（L1）的被推荐人脱敏列表与汇总计数；"
                    + "status=ALL|REGISTERED|PURCHASED 过滤（非白名单值按 ALL 处理）。"
                    + "DTO 零层级字段，不暴露关系链层级。")
    @GetMapping("/my-referrals")
    public ApiResponse<MyReferralsResponse> myReferrals(
            @RequestParam(name = "status", defaultValue = "ALL") String status) {
        String openid = CurrentOpenid.require();
        return ApiResponse.ok(referralQueryService.myReferrals(openid, status));
    }

    @Operation(summary = "确认邀请并绑定关系链",
            description = "用户在落地页显式确认邀请后调用（登录态）。仅首次绑定可写、幂等；"
                    + "解码失败/自我邀请/已绑定均不报错。返回 {bound: true/false}。")
    @PostMapping("/confirm-binding")
    public ApiResponse<Map<String, Boolean>> confirmBinding(@RequestBody ConfirmBindingRequest req) {
        String openid = CurrentOpenid.require();
        boolean bound = referralBindingService.confirmBinding(openid, req.refId());
        return ApiResponse.ok(Map.of("bound", bound));
    }

    @Operation(summary = "生成小程序码图片",
            description = "用当前登录用户的 ref_id 作为 scene 参数，调用微信 wxacode.getUnlimited 返回小程序码图片（data URL 格式）。"
                    + "page 默认 \"pages/index/index\"，envVersion 默认 \"release\"（可选 trial/develop）。")
    @PostMapping("/wxacode")
    public ApiResponse<WxacodeResponse> wxacode(@RequestBody WxacodeRequest req) {
        String openid = CurrentOpenid.require();
        CendUser user = userRepo.findByOpenid(openid)
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));
        // 微信 scene 最长 32 字符（标准 encode 约 48 字符会被拒，errcode 40169）→ 用紧凑形态。
        String scene = refIdCodec.encodeScene(user.getId());
        String dataUrl = wxMiniCodeClient.getUnlimitedWxaCode(scene, req.page(), req.envVersion());
        return ApiResponse.ok(new WxacodeResponse(dataUrl));
    }

    @Operation(summary = "查询我的邀请人",
            description = "登录态。返回当前用户的直接邀请人（L1）脱敏资料；自然流量用户返回 null。"
                    + "仅展示脱敏昵称+头像+绑定时间，零可定位 PII，不暴露层级。")
    @GetMapping("/my-inviter")
    public ApiResponse<MyInviterResponse> myInviter() {
        String openid = CurrentOpenid.require();
        CendUser me = userRepo.findByOpenid(openid)
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));
        if (me.getInviterId() == null) {
            return ApiResponse.ok(null); // 自然流量：无邀请人
        }
        CendUser inviter = userRepo.findById(me.getInviterId()).orElse(null);
        if (inviter == null) {
            return ApiResponse.ok(null); // 邀请人记录不存在（数据一致性问题，静默）
        }
        return ApiResponse.ok(new MyInviterResponse(
                NicknameMasker.mask(inviter.getNickname()),
                displayNameResolver.resolve(inviter.getOpenid(), inviter.getNickname()),
                inviter.getAvatarUrl(),
                me.getCreatedAt() != null ? me.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")) : null));
    }

    /**
     * 邀请确认页推荐人脱敏资料（014）。
     *
     * @param nicknameMasked 脱敏昵称（首字*尾字）
     * @param avatarUrl      头像 URL（微信头像，非可定位 PII）
     */
    public record ResolveInviterResponse(String nicknameMasked, String avatarUrl) {}

    /**
     * 确认邀请请求体（014）。
     *
     * @param refId 推广 ref_id（推广者 user_id 的 HMAC 签名形式）。
     */
    public record ConfirmBindingRequest(String refId) {}

    /**
     * 小程序码生成请求体。
     *
     * @param page       小程序页面路径，默认 "pages/index/index"
     * @param envVersion 小程序版本，默认 "release"（可选 "trial"/"develop"）
     */
    public record WxacodeRequest(String page, String envVersion) {
        public WxacodeRequest {
            if (page == null || page.isBlank()) {
                page = "pages/index/index";
            }
            if (envVersion == null || envVersion.isBlank()) {
                envVersion = "release";
            }
        }
    }

    /**
     * 小程序码生成响应体。
     *
     * @param dataUrl Base64 编码的 JPEG data URL（data:image/jpeg;base64,...）
     */
    public record WxacodeResponse(String dataUrl) {}
}
