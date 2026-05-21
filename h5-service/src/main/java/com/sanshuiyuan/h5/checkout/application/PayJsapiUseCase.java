package com.sanshuiyuan.h5.checkout.application;

import com.sanshuiyuan.h5.checkout.api.dto.WxPayParamsResponse;
import com.sanshuiyuan.h5.checkout.domain.H5Order;
import com.sanshuiyuan.h5.checkout.domain.OrderStatus;
import com.sanshuiyuan.h5.checkout.infra.repository.H5OrderRepository;
import com.sanshuiyuan.h5.checkout.infra.wxpay.WxPayClient;
import com.sanshuiyuan.h5.common.BizException;
import com.sanshuiyuan.h5.common.ErrorCode;
import org.springframework.stereotype.Service;

@Service
public class PayJsapiUseCase {

    private final H5OrderRepository orderRepo;
    private final WxPayClient wxPayClient;

    public PayJsapiUseCase(H5OrderRepository orderRepo, WxPayClient wxPayClient) {
        this.orderRepo = orderRepo;
        this.wxPayClient = wxPayClient;
    }

    public WxPayParamsResponse execute(String openid, Long orderId) {
        H5Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new BizException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.getOpenid().equals(openid)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (order.getStatus() != OrderStatus.PENDING_PAY) {
            throw new BizException(ErrorCode.ORDER_STATUS_CONFLICT);
        }

        WxPayClient.PrepayResult result = wxPayClient.jsapiPrepay(
                order.getOrderNo(), openid, order.getAmountCents(),
                "三水元设备购置-" + order.getModelCode());

        order.setWxPrepayId(result.prepayId());
        orderRepo.save(order);

        return new WxPayParamsResponse(
                result.appId(), result.timeStamp(), result.nonceStr(),
                result.packageVal(), result.signType(), result.paySign());
    }
}
