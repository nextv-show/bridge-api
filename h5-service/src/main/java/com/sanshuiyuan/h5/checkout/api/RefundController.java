package com.sanshuiyuan.h5.checkout.api;

import com.sanshuiyuan.h5.checkout.api.dto.RefundResultDto;
import com.sanshuiyuan.h5.checkout.application.RefundService;
import com.sanshuiyuan.h5.auth.CurrentOpenid;
import com.sanshuiyuan.h5.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/h5/order")
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
