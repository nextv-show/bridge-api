package com.sanshuiyuan.h5.checkout.api;

import com.sanshuiyuan.h5.checkout.api.dto.PayJsapiRequest;
import com.sanshuiyuan.h5.checkout.api.dto.WxPayParamsResponse;
import com.sanshuiyuan.h5.checkout.application.PayJsapiUseCase;
import com.sanshuiyuan.h5.common.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/h5/pay")
@Tag(name = "Pay")
public class PayController {

    private final PayJsapiUseCase payJsapiUseCase;

    public PayController(PayJsapiUseCase payJsapiUseCase) {
        this.payJsapiUseCase = payJsapiUseCase;
    }

    @PostMapping("/jsapi")
    public ApiResponse<WxPayParamsResponse> jsapi(@Valid @RequestBody PayJsapiRequest req) {
        // TODO: extract openid from JWT once auth is wired
        String openid = "stub-openid";
        return ApiResponse.ok(payJsapiUseCase.execute(openid, req.orderId()));
    }
}
