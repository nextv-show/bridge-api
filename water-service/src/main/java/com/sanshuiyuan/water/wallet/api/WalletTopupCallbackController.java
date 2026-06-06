package com.sanshuiyuan.water.wallet.api;

import com.sanshuiyuan.water.wallet.application.TopupCallbackUseCase;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 微信支付充值回调入口（/api/w/wallet/topup/callback）。白名单放行（无需 JWT）。
 * 返回 {@code {"code":"SUCCESS"}} 或 {@code {"code":"FAIL"}}，微信据此决定是否重试。
 */
@RestController
@RequestMapping("/api/w/wallet/topup")
public class WalletTopupCallbackController {

    private final TopupCallbackUseCase callbackUseCase;

    public WalletTopupCallbackController(TopupCallbackUseCase callbackUseCase) {
        this.callbackUseCase = callbackUseCase;
    }

    @PostMapping("/callback")
    public Map<String, Object> callback(
            @RequestBody String body,
            @RequestHeader(value = "Wechatpay-Signature", required = false) String signature,
            @RequestHeader(value = "Wechatpay-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "Wechatpay-Nonce", required = false) String nonce,
            @RequestHeader(value = "Wechatpay-Serial", required = false) String serial) {

        String code = callbackUseCase.handleCallback(body, signature, timestamp, nonce, serial);
        return Map.of("code", code);
    }
}
