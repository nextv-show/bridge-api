package com.sanshuiyuan.cend.checkout.api;

import com.sanshuiyuan.cend.auth.CurrentOpenid;
import com.sanshuiyuan.cend.checkout.application.SubscribeSigningService;
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
 * 小程序认购签约编排：实名+人脸核身由腾讯电子签小程序承载。
 * h5 经 {@link SubscribeSigningService} 调 ess 生成/发起/查询合同，并在签署完成时落实名（KYC PASS）。
 */
@RestController
@RequestMapping("/api/h5/subscribe")
@Tag(name = "Subscribe")
public class SubscribeSigningController {

    private final SubscribeSigningService service;

    public SubscribeSigningController(SubscribeSigningService service) {
        this.service = service;
    }

    @PostMapping("/sign-start")
    @Operation(summary = "发起认购签约：生成合同并返回腾讯电子签跳转参数")
    public ApiResponse<SignStartResponse> signStart(@Valid @RequestBody SignStartRequest req,
                                                    @RequestHeader(value = "Authorization", required = false) String authorization) {
        String openid = CurrentOpenid.require();
        SubscribeSigningService.SignStartResult r = service.start(
                openid, authorization, req.userId(), req.specId(), req.realName(), req.idCardNo(), req.phone());
        return ApiResponse.ok(new SignStartResponse(r.contractId(), r.contractNo(), r.signParams()));
    }

    @GetMapping("/sign-status")
    @Operation(summary = "查询签约状态；SIGNED 即视为实名通过（写 KYC PASS）")
    public ApiResponse<SignStatusResponse> signStatus(@RequestParam Long contractId,
                                                      @RequestHeader(value = "Authorization", required = false) String authorization) {
        String openid = CurrentOpenid.require();
        String status = service.status(openid, authorization, contractId);
        return ApiResponse.ok(new SignStatusResponse(contractId, status));
    }

    public record SignStartRequest(
            @NotBlank String specId,
            Long userId,
            @NotBlank String realName,
            @NotBlank String idCardNo,
            @NotBlank String phone) {}

    public record SignStartResponse(Long contractId, String contractNo, Object signParams) {}

    public record SignStatusResponse(Long contractId, String status) {}
}
