package com.sanshuiyuan.h5.checkout.application;

import com.sanshuiyuan.h5.checkout.domain.H5Order;
import com.sanshuiyuan.h5.checkout.domain.OrderStatus;
import com.sanshuiyuan.h5.checkout.infra.repository.H5OrderRepository;
import com.sanshuiyuan.h5.checkout.infra.wxpay.WxPayCallbackVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PayCallbackUseCase {

    private static final Logger log = LoggerFactory.getLogger(PayCallbackUseCase.class);

    private final WxPayCallbackVerifier verifier;
    private final H5OrderRepository orderRepo;
    private final OrderPaymentCompletionService completionService;

    public PayCallbackUseCase(WxPayCallbackVerifier verifier, H5OrderRepository orderRepo,
                              OrderPaymentCompletionService completionService) {
        this.verifier = verifier;
        this.orderRepo = orderRepo;
        this.completionService = completionService;
    }

    @Transactional
    public String handleCallback(String body, String signature, String timestamp, String nonce, String serial) {
        WxPayCallbackVerifier.VerifyResult result = verifier.verifyAndDecrypt(body, signature, timestamp, nonce, serial);
        if (!result.valid()) {
            return "FAIL";
        }

        // 验签通过后查单。幂等（payment_inbox 唯一键）由 OrderPaymentCompletionService 持有。
        var orderOpt = orderRepo.findByOrderNo(result.outTradeNo());
        if (orderOpt.isEmpty()) {
            log.warn("Order not found for outTradeNo={}", result.outTradeNo());
            return "FAIL";
        }

        H5Order order = orderOpt.get();
        if (order.getStatus() == OrderStatus.CLOSED) {
            log.warn("Order {} is CLOSED but payment received — needs manual reconciliation", order.getOrderNo());
            return "SUCCESS";
        }
        if (order.getStatus() != OrderStatus.PENDING_PAY) {
            log.info("Order {} already processed (status={})", order.getOrderNo(), order.getStatus());
            return "SUCCESS";
        }

        // 仅当微信交易状态为 SUCCESS 才置为已支付；其它状态（如 CLOSED/PAYERROR）ack 但不改单。
        if (result.tradeState() != null && !"SUCCESS".equals(result.tradeState())) {
            log.warn("Order {} 回调 tradeState={} 非 SUCCESS，幂等返回不改单",
                    order.getOrderNo(), result.tradeState());
            return "SUCCESS";
        }

        // SUCCESS 路径：交由共享完成逻辑落账（幂等插入 inbox + 改单 + 双写 + 冻结返利 + 发事件）。
        completionService.completePaid(order, result.transactionId(), result.rawBody());
        return "SUCCESS";
    }
}
