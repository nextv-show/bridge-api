package com.sanshuiyuan.h5.checkout.application;

import com.sanshuiyuan.h5.checkout.api.dto.RefundResultDto;
import com.sanshuiyuan.h5.checkout.domain.H5Order;
import com.sanshuiyuan.h5.checkout.domain.OrderStatus;
import com.sanshuiyuan.h5.checkout.domain.Refund;
import com.sanshuiyuan.h5.checkout.domain.RefundStatus;
import com.sanshuiyuan.h5.checkout.infra.repository.H5OrderRepository;
import com.sanshuiyuan.h5.checkout.infra.repository.RefundRepository;
import com.sanshuiyuan.h5.checkout.infra.wxpay.WxRefundClient;
import com.sanshuiyuan.h5.common.BizException;
import com.sanshuiyuan.h5.common.ErrorCode;
import com.sanshuiyuan.h5.rebate.application.RebateService;
import com.sanshuiyuan.h5.realtime.H5RealtimeBroadcaster;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock H5OrderRepository orderRepo;
    @Mock RefundRepository refundRepo;
    @Mock WxRefundClient wxRefundClient;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock RebateService rebateService;
    @Mock H5RealtimeBroadcaster realtimeBroadcaster;

    private RefundService createService() {
        return new RefundService(orderRepo, refundRepo, wxRefundClient, eventPublisher, rebateService, realtimeBroadcaster);
    }

    // ─── helpers ───

    private H5Order paidOrder(Long id, String openid, long amountCents,
                              LocalDateTime cooldownEndAt) {
        H5Order order = H5Order.create("ORD" + id, openid, "spec-1",
                "MODEL-X", amountCents, "WX_JSAPI");
        setField(order, "id", id);
        order.markPaid("wx-txn-123", "SN-" + id, cooldownEndAt);
        return order;
    }

    private void setField(Object target, String name, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Refund existingRefund(Long orderId, String refundNo, Long amountCents,
                                  RefundStatus status) {
        Refund r = Refund.create(orderId, refundNo, amountCents);
        if (status == RefundStatus.SUCCESS) {
            r.markSuccess("wx-refund-id-1");
        } else if (status == RefundStatus.FAILED) {
            r.markFailed();
        }
        return r;
    }

    // ─── 场景 1: 冷静期内退款成功 ───

    @Test
    void requestRefund_withinCooldown_success() {
        LocalDateTime cooldownEnd = LocalDateTime.now().plusHours(20);
        H5Order order = paidOrder(1L, "user-A", 29900L, cooldownEnd);

        when(orderRepo.findById(1L)).thenReturn(Optional.of(order));
        when(refundRepo.findByOrderId(1L)).thenReturn(Optional.empty());
        when(refundRepo.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepo.save(any(H5Order.class))).thenAnswer(inv -> inv.getArgument(0));

        RefundResultDto result = createService().requestRefund(1L, "user-A");

        assertThat(result.refundNo()).startsWith("RF");
        assertThat(result.status()).isEqualTo("processing");
        assertThat(result.amountCents()).isEqualTo(29900L);
        assertThat(result.refundedAt()).isNull();

        // verify order status changed to REFUNDING
        ArgumentCaptor<H5Order> orderCaptor = ArgumentCaptor.forClass(H5Order.class);
        verify(orderRepo).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.REFUNDING);

        // verify wx refund was called with server-side amount
        verify(wxRefundClient).refund(eq("ORD1"), anyString(), eq(29900L));
    }

    // ─── 场景 2: 冷静期外退款拒绝 (COOLDOWN_EXPIRED) ───

    @Test
    void requestRefund_cooldownExpired_throws409() {
        LocalDateTime cooldownEnd = LocalDateTime.now().minusHours(1);
        H5Order order = paidOrder(2L, "user-B", 50000L, cooldownEnd);

        when(orderRepo.findById(2L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> createService().requestRefund(2L, "user-B"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).errorCode())
                        .isEqualTo(ErrorCode.COOLDOWN_EXPIRED));

        verify(refundRepo, never()).save(any());
    }

    // ─── 场景 3: 非 PAID 状态拒绝 (ORDER_NOT_REFUNDABLE) ───

    @Test
    void requestRefund_orderNotPaid_throws409() {
        H5Order order = paidOrder(3L, "user-C", 10000L, LocalDateTime.now().plusHours(5));
        // force status to REFUNDING
        order.markRefunding();

        when(orderRepo.findById(3L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> createService().requestRefund(3L, "user-C"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).errorCode())
                        .isEqualTo(ErrorCode.ORDER_NOT_REFUNDABLE));
    }

    @Test
    void requestRefund_orderPendingPay_throws409() {
        H5Order order = H5Order.create("ORD99", "user-C", "spec-1",
                "MODEL-X", 10000L, "WX_JSAPI");
        setField(order, "id", 99L);
        // status is PENDING_PAY by default

        when(orderRepo.findById(99L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> createService().requestRefund(99L, "user-C"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).errorCode())
                        .isEqualTo(ErrorCode.ORDER_NOT_REFUNDABLE));
    }

    // ─── 场景 4: 非本人订单拒绝 (403) ───

    @Test
    void requestRefund_notOwner_throws403() {
        H5Order order = paidOrder(4L, "user-D", 30000L, LocalDateTime.now().plusHours(10));

        when(orderRepo.findById(4L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> createService().requestRefund(4L, "attacker-X"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).errorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    // ─── 场景 5: 重复提交幂等（返回同一退款单） ───

    @Test
    void requestRefund_duplicateRequest_idempotent() {
        LocalDateTime cooldownEnd = LocalDateTime.now().plusHours(5);
        H5Order order = paidOrder(5L, "user-E", 88800L, cooldownEnd);
        Refund existing = existingRefund(5L, "RF12345678905", 88800L, RefundStatus.PROCESSING);

        when(orderRepo.findById(5L)).thenReturn(Optional.of(order));
        when(refundRepo.findByOrderId(5L)).thenReturn(Optional.of(existing));

        RefundResultDto result = createService().requestRefund(5L, "user-E");

        assertThat(result.refundNo()).isEqualTo("RF12345678905");
        assertThat(result.status()).isEqualTo("processing");
        assertThat(result.amountCents()).isEqualTo(88800L);

        // no new refund should be saved
        verify(refundRepo, never()).save(any());
        verify(wxRefundClient, never()).refund(anyString(), anyString(), any());
    }

    @Test
    void requestRefund_duplicateRequest_successAlreadyDone_returnsRefundedAt() {
        LocalDateTime cooldownEnd = LocalDateTime.now().plusHours(5);
        H5Order order = paidOrder(55L, "user-E2", 77700L, cooldownEnd);
        Refund existing = existingRefund(55L, "RF_EXISTING_55", 77700L, RefundStatus.SUCCESS);

        when(orderRepo.findById(55L)).thenReturn(Optional.of(order));
        when(refundRepo.findByOrderId(55L)).thenReturn(Optional.of(existing));

        RefundResultDto result = createService().requestRefund(55L, "user-E2");

        assertThat(result.refundNo()).isEqualTo("RF_EXISTING_55");
        assertThat(result.status()).isEqualTo("success");
        assertThat(result.refundedAt()).isNotNull();

        verify(refundRepo, never()).save(any());
        verify(wxRefundClient, never()).refund(anyString(), anyString(), any());
    }

    // ─── 场景 6: 退款金额取服务端 paidAmount ───

    @Test
    void requestRefund_amountFromServerSide_notFromClient() {
        LocalDateTime cooldownEnd = LocalDateTime.now().plusHours(12);
        H5Order order = paidOrder(6L, "user-F", 680000L, cooldownEnd);

        when(orderRepo.findById(6L)).thenReturn(Optional.of(order));
        when(refundRepo.findByOrderId(6L)).thenReturn(Optional.empty());
        when(refundRepo.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepo.save(any(H5Order.class))).thenAnswer(inv -> inv.getArgument(0));

        RefundResultDto result = createService().requestRefund(6L, "user-F");

        // amount must come from order.amountCents (server-side)
        assertThat(result.amountCents()).isEqualTo(680000L);

        ArgumentCaptor<Refund> refundCaptor = ArgumentCaptor.forClass(Refund.class);
        verify(refundRepo).save(refundCaptor.capture());
        assertThat(refundCaptor.getValue().getAmountCents()).isEqualTo(680000L);

        // wx call also uses server amount
        verify(wxRefundClient).refund(anyString(), anyString(), eq(680000L));
    }

    // ─── 额外: 订单不存在 ───

    @Test
    void requestRefund_orderNotFound_throws404() {
        when(orderRepo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> createService().requestRefund(999L, "anyone"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).errorCode())
                        .isEqualTo(ErrorCode.ORDER_NOT_FOUND));
    }

    // ─── 额外: cooldownEndAt 为 null 仍然允许退款（无冷却期限制） ───

    @Test
    void requestRefund_noCooldownField_success() {
        H5Order order = paidOrder(7L, "user-G", 10000L, null);

        when(orderRepo.findById(7L)).thenReturn(Optional.of(order));
        when(refundRepo.findByOrderId(7L)).thenReturn(Optional.empty());
        when(refundRepo.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepo.save(any(H5Order.class))).thenAnswer(inv -> inv.getArgument(0));

        RefundResultDto result = createService().requestRefund(7L, "user-G");

        assertThat(result.refundNo()).startsWith("RF");
        assertThat(result.status()).isEqualTo("processing");
    }

    // ─── 额外: 微信退款调用失败时回滚状态 ───

    @Test
    void requestRefund_wxCallFails_revertOrderAndThrow() {
        LocalDateTime cooldownEnd = LocalDateTime.now().plusHours(5);
        H5Order order = paidOrder(8L, "user-H", 20000L, cooldownEnd);

        when(orderRepo.findById(8L)).thenReturn(Optional.of(order));
        when(refundRepo.findByOrderId(8L)).thenReturn(Optional.empty());
        when(refundRepo.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepo.save(any(H5Order.class))).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.doThrow(new RuntimeException("wx error"))
                .when(wxRefundClient).refund(anyString(), anyString(), any());

        assertThatThrownBy(() -> createService().requestRefund(8L, "user-H"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).errorCode())
                        .isEqualTo(ErrorCode.INTERNAL_ERROR));
    }
}
