package com.sanshuiyuan.asset.api;

import com.sanshuiyuan.asset.application.PayCallbackUseCase;
import com.sanshuiyuan.asset.infra.wxpay.VerifiedCallback;
import com.sanshuiyuan.asset.infra.wxpay.WxPayCallbackVerifier;
import com.sanshuiyuan.asset.infra.wxpay.WxPaySignatureException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "微信支付回调")
@RestController
@RequestMapping("/wxpay")
public class WxPayCallbackController {

    private final PayCallbackUseCase payCallbackUseCase;
    private final WxPayCallbackVerifier verifier;

    public WxPayCallbackController(PayCallbackUseCase payCallbackUseCase, WxPayCallbackVerifier verifier) {
        this.payCallbackUseCase = payCallbackUseCase;
        this.verifier = verifier;
    }

    @Operation(summary = "支付回调通知")
    @PostMapping("/callback")
    public ResponseEntity<Map<String, String>> callback(@RequestHeader Map<String, String> headers,
                                                         @RequestBody String body) {
        VerifiedCallback verified;
        try {
            verified = verifier.verify(headers, body);
        } catch (WxPaySignatureException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("code", "FAIL", "message", "签名验证失败"));
        }
        payCallbackUseCase.handleCallback(verified.transactionId(), verified.orderId(), body);
        return ResponseEntity.ok(Map.of("code", "SUCCESS", "message", "成功"));
    }

    // Internal helper for testing/demo (dev tool, unchanged behaviour).
    @PostMapping("/simulate-callback")
    public void simulateCallback(@RequestParam String transactionId, @RequestParam Long orderId) {
        payCallbackUseCase.handleCallback(transactionId, orderId, "{}");
    }
}
