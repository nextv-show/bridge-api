package com.sanshuiyuan.asset.api;

import com.sanshuiyuan.asset.application.WalletService;
import com.sanshuiyuan.asset.infra.wxpay.WalletCallbackResult;
import com.sanshuiyuan.asset.infra.wxpay.WalletPayCallbackVerifier;
import com.sanshuiyuan.asset.infra.wxpay.WxPaySignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 水费充值的微信支付 V3 回调（notify-url = wxpay.wallet-notify-url）。
 * 验签+解密成功且 tradeState=SUCCESS 时，按 out_trade_no("WR{id}") 入账钱包（幂等）。
 */
@RestController
@RequestMapping("/wxpay")
public class WalletPayCallbackController {

    private static final Logger log = LoggerFactory.getLogger(WalletPayCallbackController.class);

    private final WalletPayCallbackVerifier verifier;
    private final WalletService walletService;

    public WalletPayCallbackController(WalletPayCallbackVerifier verifier, WalletService walletService) {
        this.verifier = verifier;
        this.walletService = walletService;
    }

    @PostMapping("/wallet-callback")
    public ResponseEntity<Map<String, String>> callback(@RequestHeader Map<String, String> headers,
                                                         @RequestBody String body) {
        WalletCallbackResult result;
        try {
            result = verifier.verify(headers, body);
        } catch (WxPaySignatureException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("code", "FAIL", "message", "签名验证失败"));
        }

        if (!"SUCCESS".equals(result.tradeState())) {
            // 非成功态（如 USERPAYING/CLOSED）：确认已收到，不入账
            return ResponseEntity.ok(Map.of("code", "SUCCESS", "message", "已接收"));
        }

        String outTradeNo = result.outTradeNo();
        if (outTradeNo == null || !outTradeNo.startsWith(WalletPayController.OUT_TRADE_PREFIX)) {
            log.warn("钱包回调 out_trade_no 非预期: {}", outTradeNo);
            return ResponseEntity.ok(Map.of("code", "SUCCESS", "message", "忽略"));
        }
        try {
            Long rechargeId = Long.valueOf(outTradeNo.substring(WalletPayController.OUT_TRADE_PREFIX.length()));
            walletService.markPaidByRecharge(rechargeId, result.transactionId());
        } catch (NumberFormatException e) {
            log.warn("钱包回调 out_trade_no 解析失败: {}", outTradeNo);
        } catch (Exception e) {
            log.error("钱包回调入账失败 out_trade_no={}: {}", outTradeNo, e.getMessage());
        }
        return ResponseEntity.ok(Map.of("code", "SUCCESS", "message", "成功"));
    }
}
