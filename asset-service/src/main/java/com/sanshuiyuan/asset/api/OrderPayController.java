package com.sanshuiyuan.asset.api;

import com.sanshuiyuan.asset.domain.Order;
import com.sanshuiyuan.asset.domain.OrderStatus;
import com.sanshuiyuan.asset.infra.client.UserServiceClient;
import com.sanshuiyuan.asset.infra.repository.OrderRepository;
import com.sanshuiyuan.asset.infra.wxpay.MpPrepayResult;
import com.sanshuiyuan.asset.infra.wxpay.MpWxPayClient;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 小程序购机订单发起微信支付（JSAPI）。
 * 流程：POST /orders 建单 → POST /orders/{id}/pay-jsapi 取支付参数
 * → 小程序 Taro.requestPayment → 微信回调 /wxpay/callback 入账（PayCallbackUseCase 建资产 + 冻结返利）。
 *
 * <p>与钱包充值（{@link WalletPayController}）的区别：购机回调验签器
 * （{@code SdkWxPayCallbackVerifier}）直接 {@code Long.valueOf(out_trade_no)} 还原 orderId，
 * 故购机 out_trade_no 必须是<b>纯数字</b>（左补零至 6~32 位），<b>不能带 "WR" 之类前缀</b>。
 */
@RestController
@RequestMapping("/orders")
public class OrderPayController {

    private final OrderRepository orderRepository;
    private final UserServiceClient userServiceClient;
    private final MpWxPayClient mpWxPayClient;

    public OrderPayController(OrderRepository orderRepository, UserServiceClient userServiceClient,
                              MpWxPayClient mpWxPayClient) {
        this.orderRepository = orderRepository;
        this.userServiceClient = userServiceClient;
        this.mpWxPayClient = mpWxPayClient;
    }

    @PostMapping("/{id}/pay-jsapi")
    public ResponseEntity<?> payJsapi(@AuthenticationPrincipal Long userId, @PathVariable("id") Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null || !order.getUserId().equals(userId)) {
            // 不存在 / 非本人订单：统一 404，不泄露他人订单是否存在。
            return ResponseEntity.status(404).body(Map.of("error", "订单不存在"));
        }
        if (order.getStatus() != OrderStatus.PENDING_PAY) {
            return ResponseEntity.status(409).body(Map.of("error", "订单状态不可支付：" + order.getStatus()));
        }
        String openid = userServiceClient.getOpenid(userId);
        if (openid == null || openid.isBlank()) {
            return ResponseEntity.status(400).body(Map.of("error", "无法获取微信支付身份(openid)，请重新登录"));
        }
        // 微信要求 out_trade_no 6~32 位；购机回调侧 Long.valueOf 还原，故纯数字左补零、绝不加前缀。
        String outTradeNo = String.format("%010d", order.getId());
        MpPrepayResult p;
        try {
            p = mpWxPayClient.jsapiPrepay(outTradeNo, openid, order.getAmountCents(), "三水元智能水机购机");
        } catch (RuntimeException e) {
            return ResponseEntity.status(502).body(Map.of("error", "发起微信支付失败：" + e.getMessage()));
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
