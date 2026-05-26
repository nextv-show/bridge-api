package com.sanshuiyuan.asset.api;

import com.sanshuiyuan.asset.application.WalletService;
import com.sanshuiyuan.asset.domain.RechargeStatus;
import com.sanshuiyuan.asset.domain.WalletRecharge;
import com.sanshuiyuan.asset.infra.client.UserServiceClient;
import com.sanshuiyuan.asset.infra.wxpay.MpPrepayResult;
import com.sanshuiyuan.asset.infra.wxpay.MpWxPayClient;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 小程序水费充值发起微信支付（JSAPI）。
 * 流程：POST /wallet/recharge 建单 → POST /wallet/recharge/{id}/pay-jsapi 取支付参数
 * → 小程序 Taro.requestPayment → 微信回调 /wxpay/wallet-callback 入账。
 */
@RestController
@RequestMapping("/wallet/recharge")
public class WalletPayController {

    static final String OUT_TRADE_PREFIX = "WR";

    private final WalletService walletService;
    private final UserServiceClient userServiceClient;
    private final MpWxPayClient mpWxPayClient;

    public WalletPayController(WalletService walletService, UserServiceClient userServiceClient,
                               MpWxPayClient mpWxPayClient) {
        this.walletService = walletService;
        this.userServiceClient = userServiceClient;
        this.mpWxPayClient = mpWxPayClient;
    }

    @PostMapping("/{id}/pay-jsapi")
    public ResponseEntity<?> payJsapi(@AuthenticationPrincipal Long userId, @PathVariable("id") Long rechargeId) {
        WalletRecharge r;
        try {
            r = walletService.getOwnedRecharge(userId, rechargeId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
        if (r.getStatus() != RechargeStatus.PENDING_PAY) {
            return ResponseEntity.status(409).body(Map.of("error", "充值单状态不可支付：" + r.getStatus()));
        }
        String openid = userServiceClient.getOpenid(userId);
        if (openid == null || openid.isBlank()) {
            return ResponseEntity.status(400).body(Map.of("error", "无法获取微信支付身份(openid)，请重新登录"));
        }
        MpPrepayResult p = mpWxPayClient.jsapiPrepay(
                OUT_TRADE_PREFIX + r.getId(), openid, r.getAmountCents(), "三水元水费充值");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("appId", p.appId());
        body.put("timeStamp", p.timeStamp());
        body.put("nonceStr", p.nonceStr());
        body.put("package", p.packageVal());
        body.put("signType", p.signType());
        body.put("paySign", p.paySign());
        body.put("real", mpWxPayClient.isReal()); // false=stub，前端走 dev 模拟支付
        return ResponseEntity.ok(body);
    }
}
