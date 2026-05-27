package com.sanshuiyuan.h5.checkout.api;

import com.sanshuiyuan.h5.checkout.application.AdminOrderProjector;
import com.sanshuiyuan.h5.checkout.domain.H5Order;
import com.sanshuiyuan.h5.checkout.domain.Refund;
import com.sanshuiyuan.h5.checkout.infra.repository.H5OrderRepository;
import com.sanshuiyuan.h5.checkout.infra.repository.RefundRepository;
import com.sanshuiyuan.h5.common.ApiResponse;
import com.sanshuiyuan.h5.common.ErrorCode;
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
    private final H5OrderRepository orderRepo;
    private final AdminOrderProjector adminOrderProjector;

    public SimulateRefundCallbackController(RefundRepository refundRepo, H5OrderRepository orderRepo,
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

        H5Order order = orderRepo.findById(refund.getOrderId()).orElseThrow();
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
