package com.sanshuiyuan.logistics.api;

import com.sanshuiyuan.logistics.infra.LogisticsEventRepository;
import com.sanshuiyuan.logistics.infra.LogisticsOrderRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * C 端物流工单查询（H5 JWT 鉴权，SecurityConfig 控制）。
 */
@RestController
@RequestMapping("/logistics/orders")
public class OrderController {

    private final LogisticsOrderRepository orderRepository;
    private final LogisticsEventRepository eventRepository;

    public OrderController(LogisticsOrderRepository orderRepository,
                           LogisticsEventRepository eventRepository) {
        this.orderRepository = orderRepository;
        this.eventRepository = eventRepository;
    }

    @GetMapping("/by-request/{requestId}/events")
    public ResponseEntity<List<Map<String, Object>>> getEvents(@PathVariable long requestId) {
        var orderOpt = orderRepository.findByRequestId(requestId);
        if (orderOpt.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        var order = orderOpt.get();
        var events = eventRepository.findByLogisticsOrderIdOrderByOccurredAtAsc(order.getId());
        var list = events.stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("logistics_order_id", e.getLogisticsOrderId());
            m.put("event_type", e.getEventType());
            m.put("occurred_at", e.getOccurredAt() != null ? e.getOccurredAt().toString() : null);
            if (e.getPayloadJson() != null) {
                try {
                    var payload = new com.fasterxml.jackson.databind.ObjectMapper()
                            .readValue(e.getPayloadJson(), Map.class);
                    m.put("note", payload.get("note"));
                } catch (Exception ignored) {
                }
            }
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }
}
