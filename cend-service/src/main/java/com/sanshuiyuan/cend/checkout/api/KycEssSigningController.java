package com.sanshuiyuan.cend.checkout.api;

import com.sanshuiyuan.cend.auth.CurrentOpenid;
import com.sanshuiyuan.cend.checkout.api.dto.SubscribeKycStatusResponse;
import com.sanshuiyuan.cend.checkout.application.DemandKycEssSigningService;
import com.sanshuiyuan.cend.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 发布用水需求前的「实名认证 / 用水需求发布承诺」电子签（spec 107）。
 *
 * <p>专用端点，不复用认购 {@code /api/c/subscribe/sign-start} 的 {@code specId} 语义；
 * 经 {@link DemandKycEssSigningService} 调 ess 生成《三水元实名认证与用水需求发布承诺书》、
 * 短信短链发起签署，并在签署完成时落实名（KYC PASS）。
 */
@RestController
@RequestMapping("/api/c/kyc")
@Tag(name = "KYC")
public class KycEssSigningController {

    private final DemandKycEssSigningService service;

    public KycEssSigningController(DemandKycEssSigningService service) {
        this.service = service;
    }

    @GetMapping("/status")
    @Operation(summary = "查询当前用户实名（KYC）状态")
    public ApiResponse<SubscribeKycStatusResponse> status() {
        String openid = CurrentOpenid.require();
        var r = service.currentKycStatus(openid);
        return ApiResponse.ok(new SubscribeKycStatusResponse(
                r.passed(), r.realNameMask(), r.idCardMask(), r.phoneMask()));
    }

    @PostMapping("/ess-sign-start")
    @Operation(summary = "发起实名承诺电子签：已 PASS 直接返回；否则生成承诺书并下发签署短信短链")
    public ApiResponse<EssSignStartResponse> essSignStart(
            @Valid @RequestBody EssSignStartRequest req,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String openid = CurrentOpenid.require();
        DemandKycEssSigningService.EssSignStartResult r = service.start(
                openid, authorization, req.userId(), req.realName(), req.idCardNo(), req.phone());
        return ApiResponse.ok(new EssSignStartResponse(
                r.alreadyPassed(), r.contractId(), r.contractNo(), r.phoneMask()));
    }

    @GetMapping("/ess-sign-status")
    @Operation(summary = "查询实名承诺签署状态；SIGNED 即视为实名通过（写 KYC PASS）")
    public ApiResponse<EssSignStatusResponse> essSignStatus(
            @RequestParam Long contractId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String openid = CurrentOpenid.require();
        String status = service.status(openid, authorization, contractId);
        return ApiResponse.ok(new EssSignStatusResponse(contractId, status));
    }

    public record EssSignStartRequest(
            @NotBlank String realName,
            @NotBlank String idCardNo,
            @NotBlank String phone,
            Long userId) {}

    /**
     * @param alreadyPassed 当前用户已实名，无需签署；此时 contractId/contractNo/phoneMask 为 null。
     * @param phoneMask     签署短信已发往的脱敏手机号（如 138****0000），供前端提示用户去短信里打开签署。
     */
    public record EssSignStartResponse(boolean alreadyPassed, Long contractId, String contractNo, String phoneMask) {}

    public record EssSignStatusResponse(Long contractId, String status) {}
}
