package com.sanshuiyuan.admin.application;

import com.sanshuiyuan.admin.domain.DeviceAsset;
import com.sanshuiyuan.admin.domain.Order;
import com.sanshuiyuan.admin.domain.User;
import com.sanshuiyuan.admin.infra.repository.DeviceAssetRepository;
import com.sanshuiyuan.admin.infra.repository.OrderRepository;
import com.sanshuiyuan.admin.infra.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderAdminServiceTest {

    @Mock private OrderRepository orderRepo;
    @Mock private AuditLogService auditLog;
    private OrderAdminService service;

    @BeforeEach
    void setUp() {
        service = new OrderAdminService(orderRepo, auditLog);
    }

    private Order createOrder(Long id, Order.Status status) {
        Order order = new Order();
        setField(order, "id", id);
        setField(order, "userId", 14821L);
        setField(order, "skuId", 1L);
        setField(order, "qty", 1);
        setField(order, "amountCents", 398800L);
        setField(order, "status", status);
        setField(order, "createdAt", LocalDateTime.now());
        return order;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void create_persistsAndLogs() {
        Order order = createOrder(1L, Order.Status.PAID);
        when(orderRepo.save(any())).thenReturn(order);

        Order saved = service.create(100L, "admin1", order, "{\"userId\":14821}");

        assertSame(order, saved);
        verify(orderRepo).save(order);
        verify(auditLog).log(eq(100L), eq("ORDER_CREATE"), eq("order"), eq("1"), contains("userId"));
    }

    @Test
    void cancel_success() {
        Order order = createOrder(1L, Order.Status.PAID);
        when(orderRepo.save(order)).thenReturn(order);

        Order saved = service.cancel(100L, "admin1", order, "test");

        assertEquals(Order.Status.CANCELLED, saved.getStatus());
        assertEquals("test", saved.getCancelReason());
        assertNotNull(saved.getCancelledAt());
        verify(auditLog).log(eq(100L), eq("ORDER_CANCEL"), eq("order"), eq("1"), contains("test"));
    }

    @Test
    void ship_success() {
        Order order = createOrder(1L, Order.Status.PAID);
        when(orderRepo.save(order)).thenReturn(order);

        Order saved = service.ship(100L, "admin1", order, "SF0001");

        assertEquals(Order.Status.SHIPPED, saved.getStatus());
        assertEquals("SF0001", saved.getShippingNo());
        assertNotNull(saved.getShippedAt());
        verify(auditLog).log(eq(100L), eq("ORDER_SHIP"), eq("order"), eq("1"), contains("SF0001"));
    }

    @Test
    void deliver_success() {
        Order order = createOrder(1L, Order.Status.SHIPPED);
        when(orderRepo.save(order)).thenReturn(order);

        Order saved = service.markDelivered(100L, "admin1", order, "ok");

        assertEquals(Order.Status.COMPLETED, saved.getStatus());
        assertNotNull(saved.getDeliveredAt());
        verify(auditLog).log(eq(100L), eq("ORDER_DELIVER"), eq("order"), eq("1"), contains("ok"));
    }

    @Test
    void ship_wrongStatus_throws() {
        Order order = createOrder(1L, Order.Status.PENDING_PAY);
        assertThrows(IllegalStateException.class, () -> service.ship(100L, "admin1", order, "SF0001"));
        verify(orderRepo, never()).save(any());
    }
}
