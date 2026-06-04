package com.sanshuiyuan.cend.checkout.api;

import com.sanshuiyuan.cend.checkout.domain.CendOrder;
import com.sanshuiyuan.cend.checkout.domain.OrderStatus;
import com.sanshuiyuan.cend.checkout.infra.repository.CendOrderRepository;
import com.sanshuiyuan.cend.checkout.infra.repository.PaymentInboxRepository;
import com.sanshuiyuan.cend.checkout.domain.PaymentInbox;
import com.sanshuiyuan.cend.common.ApiResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/c/pay")
@Profile("dev")
public class SimulateCallbackController {

    private final CendOrderRepository orderRepo;
    private final PaymentInboxRepository inboxRepo;

    public SimulateCallbackController(CendOrderRepository orderRepo, PaymentInboxRepository inboxRepo) {
        this.orderRepo = orderRepo;
        this.inboxRepo = inboxRepo;
    }

    @PostMapping("/simulate-callback")
    public ResponseEntity<ApiResponse<Map<String, Object>>> simulate(@RequestBody Map<String, String> req) {
        String orderNo = req.get("orderNo");
        if (orderNo == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(com.sanshuiyuan.cend.common.ErrorCode.VALIDATION_FAILED, "orderNo required"));
        }

        var orderOpt = orderRepo.findByOrderNo(orderNo);
        if (orderOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error(com.sanshuiyuan.cend.common.ErrorCode.ORDER_NOT_FOUND));
        }

        CendOrder order = orderOpt.get();
        if (order.getStatus() != OrderStatus.PENDING_PAY) {
            return ResponseEntity.status(409)
                    .body(ApiResponse.error(com.sanshuiyuan.cend.common.ErrorCode.ORDER_STATUS_CONFLICT));
        }

        String txnId = "SIMULATE-" + UUID.randomUUID().toString().substring(0, 16);
        String sn = "SN-PENDING-" + order.getOrderNo();

        // Idempotent inbox insert
        if (inboxRepo.findByTransactionId(txnId).isEmpty()) {
            inboxRepo.save(PaymentInbox.create(txnId, orderNo, "{}"));
        }

        order.markPaid(txnId, sn, LocalDateTime.now().plusHours(24));
        orderRepo.save(order);

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "orderNo", orderNo,
                "status", order.getStatus().name(),
                "sn", sn,
                "transactionId", txnId
        )));
    }
}
