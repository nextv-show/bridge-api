package com.sanshuiyuan.cend.checkout.api;

import com.sanshuiyuan.cend.checkout.application.PayCallbackUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/h5/pay")
public class PayCallbackController {

    private static final Logger log = LoggerFactory.getLogger(PayCallbackController.class);

    private final PayCallbackUseCase payCallbackUseCase;

    public PayCallbackController(PayCallbackUseCase payCallbackUseCase) {
        this.payCallbackUseCase = payCallbackUseCase;
    }

    @PostMapping("/callback")
    public ResponseEntity<Map<String, String>> callback(
            @RequestBody String body,
            @RequestHeader(value = "Wechatpay-Signature", required = false) String signature,
            @RequestHeader(value = "Wechatpay-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "Wechatpay-Nonce", required = false) String nonce,
            @RequestHeader(value = "Wechatpay-Serial", required = false) String serial) {

        String code = payCallbackUseCase.handleCallback(body, signature, timestamp, nonce, serial);
        return ResponseEntity.ok(Map.of("code", code));
    }
}
