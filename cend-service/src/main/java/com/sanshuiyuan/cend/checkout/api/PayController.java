package com.sanshuiyuan.cend.checkout.api;

import com.sanshuiyuan.cend.checkout.api.dto.PayJsapiRequest;
import com.sanshuiyuan.cend.checkout.api.dto.WxPayParamsResponse;
import com.sanshuiyuan.cend.checkout.application.PayJsapiUseCase;
import com.sanshuiyuan.cend.auth.CurrentOpenid;
import com.sanshuiyuan.cend.common.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/c/pay")
@Tag(name = "Pay")
public class PayController {

    private final PayJsapiUseCase payJsapiUseCase;

    public PayController(PayJsapiUseCase payJsapiUseCase) {
        this.payJsapiUseCase = payJsapiUseCase;
    }

    @PostMapping("/jsapi")
    public ApiResponse<WxPayParamsResponse> jsapi(@Valid @RequestBody PayJsapiRequest req) {
        String openid = CurrentOpenid.require();
        boolean mini = CurrentOpenid.isMini();
        String payOpenid = CurrentOpenid.requirePayOpenid();
        return ApiResponse.ok(payJsapiUseCase.execute(openid, req.orderId(), mini, payOpenid));
    }
}
