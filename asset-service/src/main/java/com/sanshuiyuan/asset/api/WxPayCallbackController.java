package com.sanshuiyuan.asset.api;

import com.sanshuiyuan.asset.application.PayCallbackUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "微信支付回调")
@RestController
@RequestMapping("/wxpay")
public class WxPayCallbackController {

    private final PayCallbackUseCase payCallbackUseCase;

    public WxPayCallbackController(PayCallbackUseCase payCallbackUseCase) {
        this.payCallbackUseCase = payCallbackUseCase;
    }

    @Operation(summary = "支付回调通知")
    @PostMapping("/callback")
    public Map<String, String> callback(@RequestBody String body) {
        // In real world, verify signature here.
        // For now, we simulate success if the body contains what we need.
        // This is a placeholder for actual V3 callback processing.
        
        // Mock processing for demo
        // Assuming we can extract order_id and transaction_id from somewhere
        // In real V3, it's encrypted.
        
        return Map.of("code", "SUCCESS", "message", "成功");
    }
    
    // Internal helper for testing/demo
    @PostMapping("/simulate-callback")
    public void simulateCallback(@RequestParam String transactionId, @RequestParam Long orderId) {
        payCallbackUseCase.handleCallback(transactionId, orderId, "{}");
    }
}
