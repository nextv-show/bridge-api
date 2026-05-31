package com.sanshuiyuan.asset.api;

import com.sanshuiyuan.asset.application.RefundService;
import com.sanshuiyuan.asset.domain.OrderStatus;
import com.sanshuiyuan.asset.infra.repository.OrderRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 内部 service-to-service 订单接口（{@code /internal/**}）。
 *
 * <p>鉴权不走 JWT，而由 {@link com.sanshuiyuan.asset.config.S2sTokenFilter} 校验 {@code X-S2S-Token}；
 * 这些接口仅供后端服务/运维内部调用，绝不暴露给前端用户。
 */
@Tag(name = "内部订单接口（S2S）")
@RestController
@RequestMapping("/internal/orders")
public class InternalOrderController {

    private final OrderRepository orderRepository;
    private final RefundService refundService;

    public InternalOrderController(OrderRepository orderRepository, RefundService refundService) {
        this.orderRepository = orderRepository;
        this.refundService = refundService;
    }

    /**
     * 批量查询：给定 userId 集合中，哪些至少有一笔 PAID（已支付）订单。
     * 供 user-service「我的推荐」回填购买状态。空/缺省入参直接返回空列表（不发空 IN 查询）。
     */
    @Operation(summary = "筛出已购机（含 PAID 订单）的用户子集")
    @PostMapping("/paid-user-ids")
    public List<Long> paidUserIds(@RequestBody(required = false) PaidUserIdsRequest request) {
        if (request == null || request.userIds() == null || request.userIds().isEmpty()) {
            return List.of();
        }
        return orderRepository.findDistinctUserIdsByUserIdInAndStatus(request.userIds(), OrderStatus.PAID);
    }

    /**
     * 标记某购机订单退款成功：订单 PAID→REFUND（幂等）并联动取消其推荐返利。
     * 退款执行方（运维/admin）在 WeChat 退款到账后调用本接口取消返利；本接口不做任何资金划转。
     */
    @Operation(summary = "购机退款成功：订单置 REFUND 并取消返利")
    @PostMapping("/{id}/refund-succeeded")
    public Map<String, Object> refundSucceeded(@PathVariable Long id) {
        int cancelled = refundService.handleRefundSucceeded(id);
        return Map.of("orderId", id, "cancelledRebates", cancelled);
    }

    /** {@code POST /paid-user-ids} 请求体：{@code {"userIds":[1,2,3]}}。 */
    public record PaidUserIdsRequest(List<Long> userIds) {}
}
