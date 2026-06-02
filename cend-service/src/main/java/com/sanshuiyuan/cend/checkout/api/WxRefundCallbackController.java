package com.sanshuiyuan.cend.checkout.api;

import com.sanshuiyuan.cend.checkout.application.RefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/h5/pay")
@Tag(name = "WxRefundCallback")
public class WxRefundCallbackController {

    private final RefundService refundService;

    public WxRefundCallbackController(RefundService refundService) {
        this.refundService = refundService;
    }

    @PostMapping("/refund-callback")
    @Operation(summary = "微信退款异步回调（白名单端点）")
    public Map<String, String> refundCallback(
            @RequestBody String body,
            @RequestHeader(value = "Wechatpay-Signature", required = false) String signature,
            @RequestHeader(value = "Wechatpay-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "Wechatpay-Nonce", required = false) String nonce,
            @RequestHeader(value = "Wechatpay-Serial", required = false) String serial) {
        refundService.handleRefundCallback(body, signature, timestamp, nonce, serial);
        return Map.of("code", "SUCCESS");
    }
}
