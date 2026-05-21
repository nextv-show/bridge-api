package com.sanshuiyuan.h5.checkout.api;

import com.sanshuiyuan.h5.checkout.api.dto.KycInitResponse;
import com.sanshuiyuan.h5.checkout.api.dto.KycVerifyRequest;
import com.sanshuiyuan.h5.checkout.api.dto.KycVerifyResponse;
import com.sanshuiyuan.h5.checkout.application.KycInitUseCase;
import com.sanshuiyuan.h5.checkout.application.KycVerifyUseCase;
import com.sanshuiyuan.h5.common.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/h5/kyc")
@Tag(name = "KYC")
public class KycController {

    private final KycInitUseCase kycInitUseCase;
    private final KycVerifyUseCase kycVerifyUseCase;

    public KycController(KycInitUseCase kycInitUseCase, KycVerifyUseCase kycVerifyUseCase) {
        this.kycInitUseCase = kycInitUseCase;
        this.kycVerifyUseCase = kycVerifyUseCase;
    }

    @PostMapping("/init")
    public ApiResponse<KycInitResponse> init() {
        // TODO: extract openid from JWT once auth is wired
        String openid = "stub-openid";
        return ApiResponse.ok(kycInitUseCase.execute(openid));
    }

    @PostMapping("/verify")
    public ApiResponse<KycVerifyResponse> verify(@Valid @RequestBody KycVerifyRequest req) {
        // TODO: extract openid from JWT once auth is wired
        String openid = "stub-openid";
        return ApiResponse.ok(kycVerifyUseCase.execute(req.certifyId(), openid));
    }
}
