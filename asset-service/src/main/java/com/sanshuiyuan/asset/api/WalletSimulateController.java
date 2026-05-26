package com.sanshuiyuan.asset.api;

import com.sanshuiyuan.asset.application.WalletService;
import com.sanshuiyuan.asset.domain.WalletRecharge;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * dev 环境模拟支付成功回调，入账钱包。
 * 生产由真实微信支付回调触发 WalletService.markPaidAndCredit，本控制器不加载（@Profile("dev")）。
 */
@RestController
@RequestMapping("/wallet/recharge")
@Profile("dev")
public class WalletSimulateController {

    private final WalletService walletService;

    public WalletSimulateController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping("/{id}/simulate-paid")
    public ResponseEntity<?> simulatePaid(
            @AuthenticationPrincipal Long userId,
            @PathVariable("id") Long rechargeId) {
        try {
            WalletRecharge r = walletService.markPaidAndCredit(userId, rechargeId, null);
            return ResponseEntity.ok(Map.of(
                    "rechargeId", r.getId(),
                    "status", r.getStatus().name(),
                    "transactionId", r.getWxTransactionId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }
}
