package com.sanshuiyuan.h5.checkout.api;

import com.sanshuiyuan.h5.checkout.api.dto.OrderCreateRequest;
import com.sanshuiyuan.h5.checkout.api.dto.OrderCreateResponse;
import com.sanshuiyuan.h5.checkout.application.CreateOrderUseCase;
import com.sanshuiyuan.h5.common.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/h5/order")
@Tag(name = "Order")
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;

    public OrderController(CreateOrderUseCase createOrderUseCase) {
        this.createOrderUseCase = createOrderUseCase;
    }

    @PostMapping("/create")
    public ApiResponse<OrderCreateResponse> create(@Valid @RequestBody OrderCreateRequest req) {
        // TODO: extract openid from JWT once auth is wired
        String openid = "stub-openid";
        return ApiResponse.ok(createOrderUseCase.execute(openid, req.specId(), req.payment()));
    }
}
