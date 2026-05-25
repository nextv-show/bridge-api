package com.sanshuiyuan.h5.myorders.api;

import com.sanshuiyuan.h5.auth.CurrentOpenid;
import com.sanshuiyuan.h5.common.ApiResponse;
import com.sanshuiyuan.h5.myorders.application.MyOrdersQueryService;
import com.sanshuiyuan.h5.myorders.dto.OrderSummaryDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/h5/orders")
@Tag(name = "MyOrders")
public class MyOrdersController {

    private final MyOrdersQueryService queryService;

    public MyOrdersController(MyOrdersQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/my")
    @Operation(summary = "我的订单列表（当前用户所有订单，按下单时间降序）")
    public ApiResponse<Page<OrderSummaryDTO>> listMyOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String openid = CurrentOpenid.require();
        return ApiResponse.ok(queryService.list(openid, page, size));
    }
}
