package com.sanshuiyuan.cend.checkout.api;

import com.sanshuiyuan.cend.checkout.api.dto.OrderCreateRequest;
import com.sanshuiyuan.cend.checkout.api.dto.OrderCreateResponse;
import com.sanshuiyuan.cend.checkout.application.CreateOrderUseCase;
import com.sanshuiyuan.cend.auth.CurrentOpenid;
import com.sanshuiyuan.cend.common.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/c/order")
@Tag(name = "Order")
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;

    public OrderController(CreateOrderUseCase createOrderUseCase) {
        this.createOrderUseCase = createOrderUseCase;
    }

    @PostMapping("/create")
    public ApiResponse<OrderCreateResponse> create(@Valid @RequestBody OrderCreateRequest req) {
        String openid = CurrentOpenid.require();
        return ApiResponse.ok(createOrderUseCase.execute(openid, req.specId(), req.payment()));
    }
}
