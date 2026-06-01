package com.sanshuiyuan.h5.checkout.api;

import com.sanshuiyuan.h5.auth.CurrentOpenid;
import com.sanshuiyuan.h5.checkout.api.dto.KycVerifyRequest;
import com.sanshuiyuan.h5.checkout.api.dto.KycVerifyResponse;
import com.sanshuiyuan.h5.checkout.api.dto.MiniKycInitRequest;
import com.sanshuiyuan.h5.checkout.api.dto.MiniKycInitResponse;
import com.sanshuiyuan.h5.checkout.application.MiniKycInitUseCase;
import com.sanshuiyuan.h5.checkout.application.MiniKycVerifyUseCase;
import com.sanshuiyuan.h5.common.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序原生人脸核身入口。与 H5 的 {@link KycController}（阿里云网页活体）并存，复用同一 KYC 落库逻辑。
 */
@RestController
@RequestMapping("/api/h5/kyc")
@Tag(name = "KYC")
public class MiniKycController {

    private final MiniKycInitUseCase initUseCase;
    private final MiniKycVerifyUseCase verifyUseCase;

    public MiniKycController(MiniKycInitUseCase initUseCase, MiniKycVerifyUseCase verifyUseCase) {
        this.initUseCase = initUseCase;
        this.verifyUseCase = verifyUseCase;
    }

    @PostMapping("/mini-init")
    public ApiResponse<MiniKycInitResponse> miniInit(@RequestBody(required = false) MiniKycInitRequest req) {
        String openid = CurrentOpenid.require();
        String realName = req == null ? null : req.realName();
        String idCardNo = req == null ? null : req.idCardNo();
        String phone = req == null ? null : req.phone();
        return ApiResponse.ok(initUseCase.execute(openid, realName, idCardNo, phone));
    }

    @PostMapping("/mini-verify")
    public ApiResponse<KycVerifyResponse> miniVerify(@Valid @RequestBody KycVerifyRequest req) {
        String openid = CurrentOpenid.require();
        return ApiResponse.ok(verifyUseCase.execute(req.certifyId(), openid));
    }
}
