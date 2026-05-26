package com.sanshuiyuan.h5.referral.api;

import com.sanshuiyuan.h5.auth.CurrentOpenid;
import com.sanshuiyuan.h5.common.ApiResponse;
import com.sanshuiyuan.h5.common.BizException;
import com.sanshuiyuan.h5.common.ErrorCode;
import com.sanshuiyuan.h5.referral.H5User;
import com.sanshuiyuan.h5.referral.H5UserRepository;
import com.sanshuiyuan.h5.referral.RefIdCodec;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
