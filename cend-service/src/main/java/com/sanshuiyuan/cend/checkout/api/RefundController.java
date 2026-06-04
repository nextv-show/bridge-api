package com.sanshuiyuan.cend.checkout.api;

import com.sanshuiyuan.cend.checkout.api.dto.RefundResultDto;
import com.sanshuiyuan.cend.checkout.application.RefundService;
import com.sanshuiyuan.cend.auth.CurrentOpenid;
import com.sanshuiyuan.cend.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/c/order")
@Tag(name = "Refund")
public class RefundController {

    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @PostMapping("/{id}/refund")
    @Operation(summary = "申请冷静期退款")
    public ApiResponse<RefundResultDto> refund(@PathVariable Long id) {
        String openid = CurrentOpenid.require();
        return ApiResponse.ok(refundService.requestRefund(id, openid));
    }
}
