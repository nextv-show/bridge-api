package com.sanshuiyuan.asset.api;

import com.sanshuiyuan.asset.api.dto.CreateOrderRequest;
import com.sanshuiyuan.asset.application.CreateOrderUseCase;
import com.sanshuiyuan.asset.domain.Order;
import com.sanshuiyuan.asset.domain.OrderStatus;
import com.sanshuiyuan.asset.infra.repository.OrderRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "订单接口")
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final OrderRepository orderRepository;

    public OrderController(CreateOrderUseCase createOrderUseCase, OrderRepository orderRepository) {
        this.createOrderUseCase = createOrderUseCase;
        this.orderRepository = orderRepository;
    }

    @Operation(summary = "创建订单")
    @PostMapping
    public Order createOrder(@AuthenticationPrincipal Long userId, @Valid @RequestBody CreateOrderRequest request) {
        return createOrderUseCase.createOrder(userId, request.skuId(), request.qty(), request.address());
    }

    @Operation(summary = "获取我的订单列表")
    @GetMapping("/mine")
    public List<Order> listMyOrders(@AuthenticationPrincipal Long userId, @RequestParam(required = false) OrderStatus status) {
        if (status != null) {
            return orderRepository.findByUserIdAndStatus(userId, status);
        }
        return orderRepository.findAll().stream().filter(o -> o.getUserId().equals(userId)).toList();
    }

    @Operation(summary = "获取订单详情")
    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrder(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return orderRepository.findByIdAndUserId(id, userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
