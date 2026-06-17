package com.sanshuiyuan.cend.myorders;

import com.sanshuiyuan.cend.auth.CurrentOpenid;
import com.sanshuiyuan.cend.common.ApiResponse;
import com.sanshuiyuan.cend.common.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 112：订单安装进度查询。H5/小程序端按 {@code /api/c/**} 前缀，由 SecurityConfig 的 H5 JWT 保护。
 */
@RestController
@RequestMapping("/api/c/orders")
@Tag(name = "OrderProgress")
public class OrderProgressController {

    private final OrderProgressService orderProgressService;

    public OrderProgressController(OrderProgressService orderProgressService) {
        this.orderProgressService = orderProgressService;
    }

    @GetMapping("/{orderNo}/progress")
    @Operation(summary = "订单安装进度（全链路聚合：支付→匹配→物流→激活，固定 9 步时间线）")
    public ApiResponse<OrderProgressResponse> progress(@PathVariable String orderNo) {
        String openid = CurrentOpenid.require();
        return orderProgressService.getProgress(orderNo, openid)
                .map(ApiResponse::ok)
                .orElseGet(() -> ApiResponse.error(ErrorCode.ORDER_NOT_FOUND, "订单不存在或不属于当前用户"));
    }
}
