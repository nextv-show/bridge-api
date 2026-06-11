package com.sanshuiyuan.cend.identity;

import com.sanshuiyuan.cend.auth.CurrentOpenid;
import com.sanshuiyuan.cend.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 跨端身份关联（C 端）：小程序"核验身份查看历史订单"入口。
 */
@RestController
@RequestMapping("/api/c/identity")
@Tag(name = "Identity")
public class IdentityLinkController {

    private final IdentityLinkService service;

    public IdentityLinkController(IdentityLinkService service) {
        this.service = service;
    }

    @PostMapping("/link-by-phone")
    @Operation(summary = "微信手机号核验，关联同一自然人在其他端的历史订单（仅解锁可见）")
    public ApiResponse<LinkByPhoneResponse> linkByPhone(@RequestBody LinkByPhoneRequest req) {
        String openid = CurrentOpenid.require();
        IdentityLinkService.LinkResult result = service.link(openid, req.code());
        return ApiResponse.ok(new LinkByPhoneResponse(result.linked(), result.message()));
    }

    public record LinkByPhoneRequest(@NotBlank String code) {}

    public record LinkByPhoneResponse(boolean linked, String message) {}
}
