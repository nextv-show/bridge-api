package com.sanshuiyuan.h5.checkout.application;

import com.sanshuiyuan.h5.checkout.domain.H5Order;
import com.sanshuiyuan.h5.checkout.domain.OrderStatus;
import com.sanshuiyuan.h5.checkout.domain.PaymentInbox;
import com.sanshuiyuan.h5.checkout.infra.repository.H5OrderRepository;
import com.sanshuiyuan.h5.checkout.infra.repository.PaymentInboxRepository;
import com.sanshuiyuan.h5.checkout.infra.wxpay.WxPayCallbackVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class PayCallbackUseCase {

    private static final Logger log = LoggerFactory.getLogger(PayCallbackUseCase.class);

    private final WxPayCallbackVerifier verifier;
    private final PaymentInboxRepository inboxRepo;
    private final H5OrderRepository orderRepo;

    public PayCallbackUseCase(WxPayCallbackVerifier verifier, PaymentInboxRepository inboxRepo,
                               H5OrderRepository orderRepo) {
        this.verifier = verifier;
        this.inboxRepo = inboxRepo;
        this.orderRepo = orderRepo;
    }

    @Transactional
    public String handleCallback(String body, String signature, String timestamp, String nonce, String serial) {
        WxPayCallbackVerifier.VerifyResult result = verifier.verifyAndDecrypt(body, signature, timestamp, nonce, serial);
        if (!result.valid()) {
            return "FAIL";
        }

        // Idempotent: insert into payment_inbox with unique key
        try {
            inboxRepo.save(PaymentInbox.create(result.transactionId(), result.outTradeNo(), result.rawBody()));
        } catch (DataIntegrityViolationException e) {
            log.info("Duplicate callback transactionId={} — idempotent return", result.transactionId());
            return "SUCCESS";
        }

        // Update order
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

        // Write SN (ASSUMPTION-Q3: placeholder SN) + cooldown_end_at
        String placeholderSn = "SN-PENDING-" + order.getOrderNo();
        LocalDateTime cooldownEnd = LocalDateTime.now().plusHours(24);
        order.markPaid(result.transactionId(), placeholderSn, cooldownEnd);
        orderRepo.save(order);

        return "SUCCESS";
    }
}
