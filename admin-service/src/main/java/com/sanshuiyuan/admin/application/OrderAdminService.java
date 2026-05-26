package com.sanshuiyuan.admin.application;

import com.sanshuiyuan.admin.domain.Order;
import com.sanshuiyuan.admin.infra.repository.OrderRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class OrderAdminService {

    private final OrderRepository orderRepo;
    private final AuditLogService auditLog;

    public OrderAdminService(OrderRepository orderRepo, AuditLogService auditLog) {
        this.orderRepo = orderRepo;
        this.auditLog = auditLog;
    }

    @Transactional
    public Order create(Long adminId, String operator, Order order, String payloadJson) {
        Order saved = orderRepo.save(order);
        auditLog.log(adminId, "ORDER_CREATE", "order", String.valueOf(saved.getId()), payloadJson);
        return saved;
    }

    @Transactional
    public Order cancel(Long adminId, String operator, Order order, String reason) {
        if (order.getStatus() == Order.Status.CANCELLED || order.getStatus() == Order.Status.REFUNDED) {
            throw new IllegalStateException("订单已关闭，无法取消");
        }
        order.setStatus(Order.Status.CANCELLED);
        order.setCancelReason(reason);
        order.setCancelledAt(LocalDateTime.now());
        Order saved = orderRepo.save(order);
        auditLog.log(adminId, "ORDER_CANCEL", "order", String.valueOf(saved.getId()), json(Map.of("reason", reason, "operator", operator)));
        return saved;
    }

    @Transactional
    public Order ship(Long adminId, String operator, Order order, String shippingNo) {
        if (order.getStatus() != Order.Status.PAID && order.getStatus() != Order.Status.ACTIVATED) {
            throw new IllegalStateException("仅已支付/履约中的订单可发货");
        }
        order.setStatus(Order.Status.SHIPPED);
        order.setShippingNo(shippingNo);
        order.setShippedAt(LocalDateTime.now());
        Order saved = orderRepo.save(order);
        auditLog.log(adminId, "ORDER_SHIP", "order", String.valueOf(saved.getId()), json(Map.of("shippingNo", shippingNo, "operator", operator)));
        return saved;
    }

    @Transactional
    public Order markDelivered(Long adminId, String operator, Order order, String note) {
        if (order.getStatus() != Order.Status.SHIPPED && order.getStatus() != Order.Status.ACTIVATED) {
            throw new IllegalStateException("仅已发货/履约中的订单可标记完成");
        }
        order.setStatus(Order.Status.COMPLETED);
        order.setDeliveredAt(LocalDateTime.now());
        Order saved = orderRepo.save(order);
        auditLog.log(adminId, "ORDER_DELIVER", "order", String.valueOf(saved.getId()), json(Map.of("note", note, "operator", operator)));
        return saved;
    }

    @Transactional
    public Order extendPaymentDeadline(Long adminId, String operator, Order order, Integer extraHours) {
        if (order.getStatus() != Order.Status.PENDING_PAY) {
            throw new IllegalStateException("仅待支付订单可延长支付期");
        }
        int hours = extraHours != null && extraHours > 0 ? extraHours : 2;
        LocalDateTime base = order.getPaymentDeadlineAt() != null ? order.getPaymentDeadlineAt() : LocalDateTime.now();
        order.setPaymentDeadlineAt(base.plusHours(hours));
        Order saved = orderRepo.save(order);
        auditLog.log(adminId, "ORDER_EXTEND_PAYMENT", "order", String.valueOf(saved.getId()), json(Map.of("extraHours", hours, "operator", operator)));
        return saved;
    }

    @Transactional
    public Order remindPayment(Long adminId, String operator, Order order, String note) {
        if (order.getStatus() != Order.Status.PENDING_PAY) {
            throw new IllegalStateException("仅待支付订单可催付");
        }
        order.setLastRemindedAt(LocalDateTime.now());
        Order saved = orderRepo.save(order);
        auditLog.log(adminId, "ORDER_REMIND_PAYMENT", "order", String.valueOf(saved.getId()), json(Map.of("note", note, "operator", operator)));
        return saved;
    }

    @Transactional
    public Order updateAddress(Long adminId, String operator, Order order, String addressSnapshot) {
        if (order.getStatus() == Order.Status.CANCELLED || order.getStatus() == Order.Status.REFUNDED) {
            throw new IllegalStateException("已关闭订单不可修改地址");
        }
        order.setAddressSnapshot(addressSnapshot != null && !addressSnapshot.isBlank() ? addressSnapshot : "{}");
        Order saved = orderRepo.save(order);
        auditLog.log(adminId, "ORDER_UPDATE_ADDRESS", "order", String.valueOf(saved.getId()), json(Map.of("addressSnapshot", order.getAddressSnapshot(), "operator", operator)));
        return saved;
    }

    private String json(Map<String, Object> values) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (var entry : values.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(escape(entry.getKey())).append('"').append(':');
            Object value = entry.getValue();
            if (value == null) {
                sb.append("null");
            } else if (value instanceof Number || value instanceof Boolean) {
                sb.append(value);
            } else {
                sb.append('"').append(escape(String.valueOf(value))).append('"');
            }
        }
        sb.append('}');
        return sb.toString();
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
