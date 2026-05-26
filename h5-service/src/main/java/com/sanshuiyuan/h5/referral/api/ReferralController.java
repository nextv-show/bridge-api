package com.sanshuiyuan.h5.referral.api;

import com.sanshuiyuan.h5.auth.CurrentOpenid;
import com.sanshuiyuan.h5.common.ApiResponse;
import com.sanshuiyuan.h5.common.BizException;
import com.sanshuiyuan.h5.common.ErrorCode;
import com.sanshuiyuan.h5.referral.H5User;
import com.sanshuiyuan.h5.referral.H5UserRepository;
import com.sanshuiyuan.h5.referral.InvalidRefIdException;
import com.sanshuiyuan.h5.referral.RefIdCodec;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 推广关系链查询接口（009 T9.5）。<b>需登录态</b>（区别于公开的 /api/h5/wx/**）。
 *
 * <p><b>合规铁律</b>：分享链接携带的是后端加密下发的当前用户 ref_id（RefIdCodec），
 * 前端不自行拼装明文 user_id；本接口只读自身、不暴露关系链层级。
 */
@RestController
@RequestMapping("/api/h5/referral")
@Tag(name = "Referral", description = "H5 推广关系链（登录态）")
public class ReferralController {

    private final H5UserRepository userRepo;
    private final RefIdCodec refIdCodec;
    private final String linkBase;

    public ReferralController(H5UserRepository userRepo,
                              RefIdCodec refIdCodec,
                              @Value("${h5.public-base-url:}") String publicBaseUrl) {
        this.userRepo = userRepo;
        this.refIdCodec = refIdCodec;
        this.linkBase = publicBaseUrl == null ? "" : publicBaseUrl.replaceAll("/+$", "");
    }

    @Operation(summary = "获取当前用户的加密 ref_id",
            description = "用于生成分享链接 ?ref_id=<加密ID>；ref_id 由后端 HMAC 签名，防篡改、不可猜测他人 id。")
    @GetMapping("/my-ref-id")
    public ApiResponse<MyRefIdResponse> myRefId() {
        String openid = CurrentOpenid.require();
        H5User user = userRepo.findByOpenid(openid)
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
        H5User inviter = userRepo.findById(inviterId).orElse(null);
        if (inviter == null) {
            return ApiResponse.ok(null); // 推荐人不存在：静默。
        }
        return ApiResponse.ok(new ResolveInviterResponse(
                maskNickname(inviter.getNickname()), inviter.getAvatarUrl()));
    }

    /**
     * 昵称脱敏：首字 + {@code *} + 尾字；2 字及以下仅「首字 + *」。空昵称返回空串。
     * 以 code point 计数，正确处理中文/含 emoji 的昵称。
     */
    static String maskNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return "";
        }
        int cpCount = nickname.codePointCount(0, nickname.length());
        String first = nickname.substring(0, nickname.offsetByCodePoints(0, 1));
        if (cpCount <= 2) {
            return first + "*";
        }
        String last = nickname.substring(nickname.offsetByCodePoints(0, cpCount - 1));
        return first + "*" + last;
    }

    /**
     * 邀请确认页推荐人脱敏资料（014）。
     *
     * @param nicknameMasked 脱敏昵称（首字*尾字）
     * @param avatarUrl      头像 URL（微信头像，非可定位 PII）
     */
    public record ResolveInviterResponse(String nicknameMasked, String avatarUrl) {}
}
