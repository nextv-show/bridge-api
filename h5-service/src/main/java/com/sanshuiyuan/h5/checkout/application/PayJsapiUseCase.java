package com.sanshuiyuan.h5.checkout.application;

import com.sanshuiyuan.h5.checkout.api.dto.WxPayParamsResponse;
import com.sanshuiyuan.h5.checkout.domain.H5Order;
import com.sanshuiyuan.h5.checkout.domain.OrderStatus;
import com.sanshuiyuan.h5.checkout.infra.repository.H5OrderRepository;
import com.sanshuiyuan.h5.checkout.infra.wxpay.WxPayClient;
import com.sanshuiyuan.h5.common.BizException;
import com.sanshuiyuan.h5.common.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class PayJsapiUseCase {

    private final H5OrderRepository orderRepo;
    private final WxPayClient wxPayClient;
    private final WxPayClient mpWxPayClient;

    public PayJsapiUseCase(H5OrderRepository orderRepo,
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
        H5Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new BizException(ErrorCode.ORDER_NOT_FOUND));

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
