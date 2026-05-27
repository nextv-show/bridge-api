package com.sanshuiyuan.h5.withdraw.api;

import com.sanshuiyuan.h5.auth.CurrentOpenid;
import com.sanshuiyuan.h5.checkout.application.KycVerifyUseCase;
import com.sanshuiyuan.h5.common.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提现入口：强制前置二次 KYC（活体）验证。
 * 当前仅提供门控，不暴露任何资金结算能力。
 */
@RestController
@RequestMapping("/api/h5/withdraw")
public class WithdrawController {

    private final KycVerifyUseCase kycVerifyUseCase;

    public WithdrawController(KycVerifyUseCase kycVerifyUseCase) {
        this.kycVerifyUseCase = kycVerifyUseCase;
    }

    @PostMapping("/require-kyc")
    public ApiResponse<WithdrawGateResponse> requireKyc() {
        String openid = CurrentOpenid.require();
        return ApiResponse.ok(new WithdrawGateResponse("KYC_REQUIRED", openid));
    }

    public record WithdrawGateResponse(String status, String openid) {}
}
