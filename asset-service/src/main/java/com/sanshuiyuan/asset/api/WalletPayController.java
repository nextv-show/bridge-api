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

    /**
     * 微信查单返回这些「已确定未支付」终态时，待支付充值单才可安全取消/关单。
     * 其余状态（QUERY_ERROR 查单失败 / USERPAYING 支付中 / ACCEPT / STUB 等）支付结果未确定，
     * 一律不取消——避免「用户已付但被取消、钱包不增」资损。
     */
    public static final java.util.Set<String> CLOSEABLE_UNPAID = java.util.Set.of("NOTPAY", "CLOSED", "REVOKED", "PAYERROR");

    /**
     * 充值单 → 微信商户订单号。微信要求 out_trade_no 6~32 位，故对 rechargeId 左补零至 10 位
     * （回调/查单侧 {@code Long.valueOf(no.substring(2))} 可还原）。下单与主动查单必须用同一构造。
     */
    public static String outTradeNo(Long rechargeId) {
        return OUT_TRADE_PREFIX + String.format("%010d", rechargeId);
    }

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
        String outTradeNo = outTradeNo(r.getId());
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

    /**
     * 取消待支付充值单（账单页「取消充值」）。
     * 资损防护：本机微信回调零送达，取消前先主动查单——若微信侧已 SUCCESS，则兜底入账而非取消
     * （返回 status=PAID, credited=true），避免「用户已付但订单被取消、钱包不增」。否则作废为 CANCELLED。
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(@AuthenticationPrincipal Long userId, @PathVariable("id") Long rechargeId) {
        WalletRecharge r;
        try {
            r = walletService.getOwnedRecharge(userId, rechargeId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
        if (r.getStatus() == RechargeStatus.CANCELLED) {
            return ResponseEntity.ok(Map.of("rechargeId", r.getId(), "status", r.getStatus().name())); // 幂等
        }
        if (r.getStatus() != RechargeStatus.PENDING_PAY) {
            return ResponseEntity.status(409).body(Map.of("error", "充值单状态不可取消：" + r.getStatus()));
        }
        // 取消前主动查单，按微信侧真实支付状态决定，避免「用户已付但被取消、钱包不增」资损。
        // stub（dev/CI，无真实支付）无资损风险，直接放行取消；真实支付下仅「已确定未支付」终态才取消。
        if (mpWxPayClient.isReal()) {
            MpWxPayClient.TradeQueryResult q = mpWxPayClient.queryOrder(outTradeNo(r.getId()));
            String state = q.tradeState();
            if ("SUCCESS".equals(state)) {
                WalletRecharge paid = walletService.markPaidByRecharge(r.getId(), q.transactionId());
                log.info("取消充值单 {} 时查到微信已支付，转兜底入账 transactionId={}", rechargeId, q.transactionId());
                return ResponseEntity.ok(Map.of("rechargeId", paid.getId(), "status", paid.getStatus().name(), "credited", true));
            }
            if (!CLOSEABLE_UNPAID.contains(state)) {
                // QUERY_ERROR / USERPAYING / ACCEPT 等：支付状态未确定，拒绝取消（保守，避免资损），待用户稍后重试。
                log.warn("取消充值单 {} 查单状态未确定（tradeState={}），拒绝取消，待用户重试", rechargeId, state);
                return ResponseEntity.status(409).body(Map.of("error", "支付状态确认中，请稍后重试"));
            }
            // NOTPAY / CLOSED / REVOKED / PAYERROR：已确定未支付，可安全取消。
        }
        try {
            WalletRecharge cancelled = walletService.cancelRecharge(userId, r.getId());
            return ResponseEntity.ok(Map.of("rechargeId", cancelled.getId(), "status", cancelled.getStatus().name()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }
}
