package com.sanshuiyuan.cend.checkout.application;

import com.sanshuiyuan.cend.checkout.api.dto.WxPayParamsResponse;
import com.sanshuiyuan.cend.checkout.domain.CendOrder;
import com.sanshuiyuan.cend.checkout.domain.OrderStatus;
import com.sanshuiyuan.cend.checkout.infra.repository.CendOrderRepository;
import com.sanshuiyuan.cend.checkout.infra.wxpay.WxPayClient;
import com.sanshuiyuan.cend.common.BizException;
import com.sanshuiyuan.cend.common.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class PayJsapiUseCase {

    private final CendOrderRepository orderRepo;
    private final WxPayClient wxPayClient;
    private final WxPayClient mpWxPayClient;

    public PayJsapiUseCase(CendOrderRepository orderRepo,
                           WxPayClient wxPayClient,
                           @Qualifier("mpWxPayClient") WxPayClient mpWxPayClient) {
        this.orderRepo = orderRepo;
        this.wxPayClient = wxPayClient;
        this.mpWxPayClient = mpWxPayClient;
    }

    /**
     * @param openid     统一身份（订单归属校验）。
     * @param orderId    订单 ID。
     * @param mini       是否小程序端（选支付通道：小程序 appId vs 公众号 appId）。
     * @param payOpenid  渠道 JSAPI 预支付 payer.openid（公众号/小程序 openid）。
     */
    public WxPayParamsResponse execute(String openid, Long orderId, boolean mini, String payOpenid) {
        CendOrder order = orderRepo.findById(orderId)
                .orElseThrow(() -> new BizException(ErrorCode.ORDER_NOT_FOUND));

        // 跨端边界（刻意严格，不按自然人聚合）：支付动作留在下单原端。
        // 订单的 JSAPI 预支付与下单端的微信 appid/payer openid 绑定，跨端（公众号↔小程序）续付会触发
        // appid 与 payer openid 不匹配。读路径（列表/详情/资产/发票）已按自然人放行可见，支付仍按原端 openid。
        if (!order.getOpenid().equals(openid)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (order.getStatus() != OrderStatus.PENDING_PAY) {
            throw new BizException(ErrorCode.ORDER_STATUS_CONFLICT);
        }

        WxPayClient client = mini ? mpWxPayClient : wxPayClient;
        WxPayClient.PrepayResult result = client.jsapiPrepay(
                order.getOrderNo(), payOpenid, order.getAmountCents(),
                "三水元设备购置-" + order.getModelCode());

        order.setWxPrepayId(result.prepayId());
        orderRepo.save(order);

        return new WxPayParamsResponse(
                result.appId(), result.timeStamp(), result.nonceStr(),
                result.packageVal(), result.signType(), result.paySign());
    }
}
