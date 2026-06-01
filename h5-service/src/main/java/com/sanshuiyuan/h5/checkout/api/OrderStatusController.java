package com.sanshuiyuan.h5.checkout.api;

import com.sanshuiyuan.h5.auth.CurrentOpenid;
import com.sanshuiyuan.h5.checkout.application.OrderPaymentCompletionService;
import com.sanshuiyuan.h5.checkout.domain.H5Order;
import com.sanshuiyuan.h5.checkout.domain.OrderStatus;
import com.sanshuiyuan.h5.checkout.infra.repository.H5OrderRepository;
import com.sanshuiyuan.h5.checkout.infra.wxpay.WxPayClient;
import com.sanshuiyuan.h5.common.ApiResponse;
import com.sanshuiyuan.h5.common.BizException;
import com.sanshuiyuan.h5.common.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单支付状态查询（前端支付后主动查单确认用）。
 * 若订单仍 PENDING_PAY，则触发一次按需主动查单（与 ReconcilePendingOrdersJob 同逻辑），
 * 命中 SUCCESS 即走 {@link OrderPaymentCompletionService} 完成落账，避免前端等定时任务 30s+。
 */
@RestController
@RequestMapping("/api/h5/order")
@Tag(name = "Order")
public class OrderStatusController {

    private static final Logger log = LoggerFactory.getLogger(OrderStatusController.class);

    private final H5OrderRepository orderRepo;
    private final WxPayClient wxPayClient;
    private final OrderPaymentCompletionService completionService;

    public OrderStatusController(H5OrderRepository orderRepo, WxPayClient wxPayClient,
                                 OrderPaymentCompletionService completionService) {
        this.orderRepo = orderRepo;
        this.wxPayClient = wxPayClient;
        this.completionService = completionService;
    }

    @GetMapping("/{id}/status")
    @Operation(summary = "查询订单支付状态（PENDING_PAY 时按需主动查单）")
    public ApiResponse<OrderStatusResponse> status(@PathVariable Long id) {
        String openid = CurrentOpenid.require();
        H5Order order = orderRepo.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.getOpenid().equals(openid)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }

        if (order.getStatus() == OrderStatus.PENDING_PAY) {
            try {
                // 查单按 mchId+outTradeNo，与支付通道 appId 无关，小程序订单同样适用。
                WxPayClient.TradeQueryResult r = wxPayClient.queryOrder(order.getOrderNo());
                if ("SUCCESS".equals(r.tradeState())) {
                    completionService.completePaid(order, r.transactionId(), "{\"recon\":\"on-demand\"}");
                    order = orderRepo.findById(id).orElse(order);
                }
            } catch (Exception e) {
                log.warn("按需查单失败 orderNo={}: {}", order.getOrderNo(), e.getMessage());
            }
        }

        return ApiResponse.ok(new OrderStatusResponse(order.getOrderNo(), order.getStatus().name()));
    }

    public record OrderStatusResponse(String orderNo, String status) {}
}
