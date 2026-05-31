package com.sanshuiyuan.asset.api;

import com.sanshuiyuan.asset.application.PurchaseRefundService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 微信退款异步回调（白名单端点，未鉴权 —— 见 SecurityConfig permitAll /wxpay/refund-callback）。
 * 验签解密在 PurchaseRefundService.handleRefundCallback（委托 WxRefundClient.parseCallback）。
 */
@RestController
@RequestMapping("/wxpay")
public class WxRefundCallbackController {

    private final PurchaseRefundService purchaseRefundService;

    public WxRefundCallbackController(PurchaseRefundService purchaseRefundService) {
        this.purchaseRefundService = purchaseRefundService;
    }

    @PostMapping("/refund-callback")
    public Map<String, String> refundCallback(
            @RequestBody String body,
            @RequestHeader(value = "Wechatpay-Signature", required = false) String signature,
            @RequestHeader(value = "Wechatpay-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "Wechatpay-Nonce", required = false) String nonce,
            @RequestHeader(value = "Wechatpay-Serial", required = false) String serial) {
        purchaseRefundService.handleRefundCallback(body, signature, timestamp, nonce, serial);
        return Map.of("code", "SUCCESS");
    }
}
