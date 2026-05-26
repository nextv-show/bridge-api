package com.sanshuiyuan.asset.api;

import com.sanshuiyuan.asset.api.dto.RechargeCreateRequest;
import com.sanshuiyuan.asset.api.dto.RechargeCreateResponse;
import com.sanshuiyuan.asset.api.dto.WalletDto;
import com.sanshuiyuan.asset.application.WalletService;
import com.sanshuiyuan.asset.domain.ConsumerWallet;
import com.sanshuiyuan.asset.domain.WalletRecharge;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;

/**
 * 消费者水费钱包 API（小程序 user-JWT）。
 * GET  /wallet                  当前用户钱包
 * POST /wallet/recharge         创建水费充值单（预收账款）
 */
@RestController
@RequestMapping("/wallet")
public class WalletController {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping
    public ResponseEntity<WalletDto> getWallet(@AuthenticationPrincipal Long userId) {
        ConsumerWallet w = walletService.getOrCreate(userId);
        return ResponseEntity.ok(new WalletDto(
                w.getBalanceCents(),
                w.getPoints(),
                w.getLitersQuota(),
                w.getDailyAvgCents(),
                w.getLastRechargeCents(),
                w.getLastRechargeAt() != null ? w.getLastRechargeAt().format(DATE) : null
        ));
    }

    @PostMapping("/recharge")
    public ResponseEntity<RechargeCreateResponse> createRecharge(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody RechargeCreateRequest req) {
        int points = req.points() != null ? req.points() : 0;
        int liters = req.liters() != null ? req.liters() : 0;
        WalletRecharge r = walletService.createRecharge(
                userId, req.amountCents(), points, liters,
                req.payment() != null ? req.payment() : "WECHAT");
        return ResponseEntity.ok(new RechargeCreateResponse(
                r.getId(), r.getAmountCents(), r.getPointsGranted(), r.getLitersGranted(), r.getStatus().name()));
    }
}
