package com.sanshuiyuan.user.referral.api;

import com.sanshuiyuan.user.domain.User;
import com.sanshuiyuan.user.infra.repository.UserRepository;
import com.sanshuiyuan.user.referral.InvalidRefIdException;
import com.sanshuiyuan.user.referral.NicknameMasker;
import com.sanshuiyuan.user.referral.RefIdCodec;
import com.sanshuiyuan.user.referral.ReferralBindingService;
import com.sanshuiyuan.user.referral.ReferralQueryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 小程序推广关系链接口（自 h5-service 忠实移植）。<b>需登录态</b>，当前用户经
 * {@code @AuthenticationPrincipal Long userId} 取得（同 {@code MeController}）。
 *
 * <p><b>合规铁律</b>：分享链接携带的是后端加密下发的当前用户 ref_id（RefIdCodec），
 * 前端不自行拼装明文 user_id；本接口只读自身、不暴露关系链层级。
 */
@RestController
@RequestMapping("/referral")
public class ReferralController {

    private final UserRepository userRepo;
    private final RefIdCodec refIdCodec;
    private final ReferralBindingService referralBindingService;
    private final ReferralQueryService referralQueryService;
    private final String linkBase;

    public ReferralController(UserRepository userRepo,
                              RefIdCodec refIdCodec,
                              ReferralBindingService referralBindingService,
                              ReferralQueryService referralQueryService,
                              @Value("${user.referral.link-base:}") String linkBase) {
        this.userRepo = userRepo;
        this.refIdCodec = refIdCodec;
        this.referralBindingService = referralBindingService;
        this.referralQueryService = referralQueryService;
        this.linkBase = linkBase == null ? "" : linkBase.replaceAll("/+$", "");
    }

    /**
     * 获取当前用户的加密 ref_id（供小程序分享）。ref_id 由后端 HMAC 签名，防篡改、不可猜测他人 id。
     * 小程序分享无需 url；linkBase 配置留空时 shareUrl 仅返回相对路径，前端可只取 refId。
     */
    @GetMapping("/my-ref-id")
    public ResponseEntity<MyRefIdResponse> myRefId(@AuthenticationPrincipal Long userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        String refId = refIdCodec.encode(user.getId());
        String shareUrl = linkBase.isBlank() ? ("/?ref_id=" + refId) : (linkBase + "/?ref_id=" + refId);
        return ResponseEntity.ok(new MyRefIdResponse(refId, shareUrl));
    }

    /**
     * 按 ref_id 解析推荐人脱敏资料（登录态）。仅返回脱敏昵称+头像，零可定位 PII；
     * ref_id 解码失败或推荐人不存在时静默返回 null（不报错、不泄露细节）。
     */
    @GetMapping("/resolve-inviter")
    public ResponseEntity<ResolveInviterResponse> resolveInviter(@RequestParam("ref_id") String refId) {
        final long inviterId;
        try {
            inviterId = refIdCodec.decode(refId);
        } catch (InvalidRefIdException e) {
            return ResponseEntity.ok(null); // 解码失败：静默，不暴露细节。
        }
        User inviter = userRepo.findById(inviterId).orElse(null);
        if (inviter == null) {
            return ResponseEntity.ok(null); // 推荐人不存在：静默。
        }
        return ResponseEntity.ok(new ResolveInviterResponse(
                NicknameMasker.mask(inviter.getNickname()), inviter.getAvatarUrl()));
    }

    /**
     * 我的推荐人列表与汇总（登录态）。返回当前用户直接推荐（L1）的被推荐人脱敏列表与汇总计数；
     * status=ALL|REGISTERED|PURCHASED 过滤（非白名单值按 ALL 处理）。DTO 零层级字段，不暴露关系链层级。
     */
    @GetMapping("/my-referrals")
    public ResponseEntity<MyReferralsResponse> myReferrals(
            @AuthenticationPrincipal Long userId,
            @RequestParam(name = "status", defaultValue = "ALL") String status) {
        return ResponseEntity.ok(referralQueryService.myReferrals(userId, status));
    }

    /**
     * 确认邀请并绑定关系链（登录态）。仅首次绑定可写、幂等；解码失败/自我邀请/已绑定均不报错。
     * 返回 {bound: true/false}。
     */
    @PostMapping("/confirm-binding")
    public ResponseEntity<Map<String, Boolean>> confirmBinding(
            @AuthenticationPrincipal Long userId,
            @RequestBody ConfirmBindingRequest req) {
        boolean bound = referralBindingService.confirmBinding(userId, req.refId());
        return ResponseEntity.ok(Map.of("bound", bound));
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
}
