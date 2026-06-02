package com.sanshuiyuan.asset.api;

import com.sanshuiyuan.asset.application.WalletService;
import com.sanshuiyuan.asset.domain.RechargeStatus;
import com.sanshuiyuan.asset.domain.WalletRecharge;
import com.sanshuiyuan.asset.infra.client.UserServiceClient;
import com.sanshuiyuan.asset.infra.wxpay.MpPrepayResult;
import com.sanshuiyuan.asset.infra.wxpay.MpWxPayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(WalletPayController.class);

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
        // 微信要求 out_trade_no 6~32 位，故对 rechargeId 左补零（回调侧 Long.valueOf 可还原）
        String outTradeNo = OUT_TRADE_PREFIX + String.format("%010d", r.getId());
        MpPrepayResult p;
        try {
            p = mpWxPayClient.jsapiPrepay(outTradeNo, openid, r.getAmountCents(), "三水元水费充值");
        } catch (RuntimeException e) {
            return ResponseEntity.status(502).body(Map.of("error", "发起微信支付失败：" + e.getMessage()));
        }

        if (!mpWxPayClient.isReal()) {
            log.warn("充值单 {} 走 stub 支付（微信支付未配置），请检查 WXPAY_* 环境变量", rechargeId);
        }

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
