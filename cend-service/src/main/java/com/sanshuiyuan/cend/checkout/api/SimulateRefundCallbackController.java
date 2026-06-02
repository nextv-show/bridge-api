package com.sanshuiyuan.cend.checkout.api;

import com.sanshuiyuan.cend.checkout.application.AdminOrderProjector;
import com.sanshuiyuan.cend.checkout.domain.CendOrder;
import com.sanshuiyuan.cend.checkout.domain.Refund;
import com.sanshuiyuan.cend.checkout.infra.repository.CendOrderRepository;
import com.sanshuiyuan.cend.checkout.infra.repository.RefundRepository;
import com.sanshuiyuan.cend.common.ApiResponse;
import com.sanshuiyuan.cend.common.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/h5/pay")
@Profile("dev")
public class SimulateRefundCallbackController {

    private final RefundRepository refundRepo;
    private final CendOrderRepository orderRepo;
    private final AdminOrderProjector adminOrderProjector;

    public SimulateRefundCallbackController(RefundRepository refundRepo, CendOrderRepository orderRepo,
                                            AdminOrderProjector adminOrderProjector) {
        this.refundRepo = refundRepo;
        this.orderRepo = orderRepo;
        this.adminOrderProjector = adminOrderProjector;
    }

    @PostMapping("/simulate-refund-callback")
    public ResponseEntity<ApiResponse<Map<String, Object>>> simulateRefund(@RequestBody Map<String, String> req) {
        String refundNo = req.get("refundNo");
        if (refundNo == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ErrorCode.VALIDATION_FAILED, "refundNo required"));
        }

        var refundOpt = refundRepo.findByRefundNo(refundNo);
        if (refundOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error(ErrorCode.ORDER_NOT_FOUND, "退款单不存在"));
        }

        Refund refund = refundOpt.get();
        refund.markSuccess("SIMULATE-REFUND-" + refundNo);

        CendOrder order = orderRepo.findById(refund.getOrderId()).orElseThrow();
        order.markRefunded();

        refundRepo.save(refund);
        orderRepo.save(order);
        // 双写：投影退款成功状态到 admin orders 表（dev 模拟回调）。
        adminOrderProjector.project(order);

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "refundNo", refundNo,
                "refundStatus", refund.getStatus().name(),
                "orderStatus", order.getStatus().name()
        )));
    }
}
