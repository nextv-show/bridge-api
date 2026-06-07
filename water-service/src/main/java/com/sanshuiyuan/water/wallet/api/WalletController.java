package com.sanshuiyuan.water.wallet.api;

import com.sanshuiyuan.water.common.ApiResponse;
import com.sanshuiyuan.water.wallet.application.GetWalletTransactionsUseCase;
import com.sanshuiyuan.water.wallet.application.GetWalletUseCase;
import com.sanshuiyuan.water.wallet.application.TopupUseCase;
import com.sanshuiyuan.water.wallet.domain.WalletTransaction;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * C 端钱包 API（/api/w/wallet）。principal=openid（H5JwtFilter 注入）。
 * 统一返回 {@code {"code":0,"data":...}}。
 */
@RestController
@RequestMapping("/api/w/wallet")
public class WalletController {

    private final GetWalletUseCase getWalletUseCase;
    private final TopupUseCase topupUseCase;
    private final GetWalletTransactionsUseCase getTransactionsUseCase;

    public WalletController(GetWalletUseCase getWalletUseCase, TopupUseCase topupUseCase,
                            GetWalletTransactionsUseCase getTransactionsUseCase) {
        this.getWalletUseCase = getWalletUseCase;
        this.topupUseCase = topupUseCase;
        this.getTransactionsUseCase = getTransactionsUseCase;
    }

    @GetMapping
    public Map<String, Object> getWallet(Principal principal) {
        GetWalletUseCase.WalletInfo info = getWalletUseCase.getWallet(principal.getName());
        return ApiResponse.ok(Map.of(
                "balanceCents", info.balanceCents(),
                "currency", info.currency()));
    }

    @PostMapping("/topup")
    public Map<String, Object> topup(Principal principal, @RequestBody TopupRequest req) {
        TopupUseCase.TopupResult result = topupUseCase.topup(principal.getName(), req.amountCents());
        return ApiResponse.ok(Map.of(
                "topupId", result.topupId(),
                "wxPrepayParams", result.wxPrepayParams()));
    }

    @GetMapping("/transactions")
    public Map<String, Object> transactions(Principal principal,
                                            @RequestParam(required = false) String cursor,
                                            @RequestParam(defaultValue = "50") int limit) {
        List<WalletTransaction> txns = getTransactionsUseCase.getTransactions(principal.getName(), cursor, limit);
        return ApiResponse.ok(txns);
    }

    public record TopupRequest(Long amountCents) {}
}
