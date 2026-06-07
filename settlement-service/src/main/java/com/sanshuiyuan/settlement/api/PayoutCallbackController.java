package com.sanshuiyuan.settlement.api;

import com.sanshuiyuan.settlement.application.payout.PayoutCallbackUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 接收微信商家转账回调（V3 签名已由网关/Nginx 校验，本服务仅处理业务逻辑）。
 */
@RestController
public class PayoutCallbackController {
    private static final Logger log = LoggerFactory.getLogger(PayoutCallbackController.class);

    private final PayoutCallbackUseCase payoutCallbackUseCase;

    public PayoutCallbackController(PayoutCallbackUseCase payoutCallbackUseCase) {
        this.payoutCallbackUseCase = payoutCallbackUseCase;
    }

    @PostMapping("/api/s/payout/callback")
    public ResponseEntity<Map<String, Object>> callback(@RequestBody Map<String, Object> body) {
        log.info("[payout] callback received: {}", body);
        try {
            payoutCallbackUseCase.handle(body);
            return ResponseEntity.ok(Map.of("code", "SUCCESS"));
        } catch (Exception e) {
            log.error("[payout] callback error", e);
            // 微信要求返回 FAIL 时重试
            return ResponseEntity.status(500).body(Map.of("code", "FAIL", "message", e.getMessage()));
        }
    }
}
