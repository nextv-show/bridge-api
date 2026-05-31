package com.sanshuiyuan.asset.api;

import com.sanshuiyuan.asset.api.dto.RefundResultDto;
import com.sanshuiyuan.asset.application.PurchaseRefundService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户自助发起购机订单冷静期退款。用户态鉴权（JWT → @AuthenticationPrincipal Long userId），
 * 落在 SecurityConfig anyRequest().authenticated() 下。
 */
@RestController
@RequestMapping("/orders")
public class RefundController {

    private final PurchaseRefundService purchaseRefundService;

    public RefundController(PurchaseRefundService purchaseRefundService) {
        this.purchaseRefundService = purchaseRefundService;
    }

    @PostMapping("/{id}/refund")
    public ResponseEntity<RefundResultDto> refund(@AuthenticationPrincipal Long userId,
                                                  @PathVariable("id") Long orderId) {
        return ResponseEntity.ok(purchaseRefundService.requestRefund(orderId, userId));
    }
}
