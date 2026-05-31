package com.sanshuiyuan.asset.application;

import com.sanshuiyuan.asset.api.dto.RefundResultDto;
import com.sanshuiyuan.asset.domain.Order;
import com.sanshuiyuan.asset.domain.OrderStatus;
import com.sanshuiyuan.asset.domain.Refund;
import com.sanshuiyuan.asset.domain.RefundStatus;
import com.sanshuiyuan.asset.infra.repository.OrderRepository;
import com.sanshuiyuan.asset.infra.repository.RefundRepository;
import com.sanshuiyuan.asset.infra.wxpay.WxRefundClient;
import com.sanshuiyuan.asset.rebate.application.RebateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 纯 Mockito 单测：购机冷静期退款流程（PurchaseRefundService）。
 * 不起 Spring / Docker。RefundService 用真实实例（注入 mock 仓库 + mock RebateService），
 * 以便验证成功路径下订单置 REFUND 且 RebateService.cancelForRefund 被调用。
 */
@ExtendWith(MockitoExtension.class)
class PurchaseRefundServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock RefundRepository refundRepository;
    @Mock WxRefundClient wxRefundClient;
    @Mock RebateService rebateService;

    private RefundService refundService;        // 真实落地服务，复用既有 handleRefundSucceeded
    private PurchaseRefundService service;

    private static final long USER_ID = 7L;
    private static final long ORDER_ID = 42L;
    private static final long AMOUNT = 19900L;

    @BeforeEach
    void setUp() {
        refundService = new RefundService(orderRepository, rebateService);
        service = new PurchaseRefundService(orderRepository, refundRepository,
                wxRefundClient, rebateService, refundService);
        service.setCooldownHours(24);
    }

    private Order paidOrder(LocalDateTime paidAt) {
        Order o = new Order();
        o.setId(ORDER_ID);
        o.setUserId(USER_ID);
        o.setSkuId(1L);
        o.setQty(1);
        o.setAmountCents(AMOUNT);
        o.setStatus(OrderStatus.PAID);
        o.setPaidAt(paidAt);
        return o;
    }

    @Test
    void requestRefund_happyPath_createsProcessingRefund_setsRefunding_callsWxWithZeroPaddedId() {
        Order order = paidOrder(LocalDateTime.now().minusHours(1));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(refundRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));

        RefundResultDto dto = service.requestRefund(ORDER_ID, USER_ID);

        // Refund 创建为 PROCESSING
        ArgumentCaptor<Refund> refundCaptor = ArgumentCaptor.forClass(Refund.class);
        verify(refundRepository).save(refundCaptor.capture());
        Refund savedRefund = refundCaptor.getValue();
        assertThat(savedRefund.getStatus()).isEqualTo(RefundStatus.PROCESSING);
        assertThat(savedRefund.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(savedRefund.getAmountCents()).isEqualTo(AMOUNT);
        assertThat(savedRefund.getRefundNo()).startsWith("RF");

        // 订单置 REFUNDING
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDING);
        verify(orderRepository).save(order);

        // wxRefundClient.refund 用 out_trade_no = 左补零 10 位 id
        ArgumentCaptor<String> outTradeNo = ArgumentCaptor.forClass(String.class);
        verify(wxRefundClient).refund(outTradeNo.capture(), eq(savedRefund.getRefundNo()), eq(AMOUNT));
        assertThat(outTradeNo.getValue()).isEqualTo(String.format("%010d", ORDER_ID));

        assertThat(dto.status()).isEqualTo("processing");
        assertThat(dto.amountCents()).isEqualTo(AMOUNT);
    }

    @Test
    void requestRefund_notOwner_rejected_noWxCall() {
        Order order = paidOrder(LocalDateTime.now().minusHours(1));
        order.setUserId(999L); // 不是请求者
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.requestRefund(ORDER_ID, USER_ID))
                .isInstanceOf(IllegalStateException.class);

        verify(refundRepository, never()).save(any());
        verify(wxRefundClient, never()).refund(any(), any(), any());
    }

    @Test
    void requestRefund_statusNotPaid_rejected() {
        Order order = paidOrder(LocalDateTime.now().minusHours(1));
        order.setStatus(OrderStatus.PENDING_PAY);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.requestRefund(ORDER_ID, USER_ID))
                .isInstanceOf(IllegalStateException.class);

        verify(wxRefundClient, never()).refund(any(), any(), any());
    }

    @Test
    void requestRefund_cooldownExpired_rejected() {
        // paidAt 远在过去（> 24h），冷静期已过
        Order order = paidOrder(LocalDateTime.now().minusHours(48));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.requestRefund(ORDER_ID, USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("冷静期");

        verify(refundRepository, never()).save(any());
        verify(wxRefundClient, never()).refund(any(), any(), any());
    }

    @Test
    void requestRefund_idempotent_whenRefundAlreadyExists() {
        Order order = paidOrder(LocalDateTime.now().minusHours(1));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        Refund existing = Refund.create(ORDER_ID, "RF-existing", AMOUNT);
        when(refundRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(existing));

        RefundResultDto dto = service.requestRefund(ORDER_ID, USER_ID);

        assertThat(dto.refundNo()).isEqualTo("RF-existing");
        // 幂等：不重复落库、不重复置 REFUNDING、不重复打微信
        verify(refundRepository, never()).save(any());
        verify(wxRefundClient, never()).refund(any(), any(), any());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void requestRefund_wxThrows_marksFailed_revertsOrderToPaid_andThrows() {
        Order order = paidOrder(LocalDateTime.now().minusHours(1));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(refundRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("wx down"))
                .when(wxRefundClient).refund(any(), any(), any());

        assertThatThrownBy(() -> service.requestRefund(ORDER_ID, USER_ID))
                .isInstanceOf(IllegalStateException.class);

        // Refund FAILED + 订单回退 PAID
        ArgumentCaptor<Refund> refundCaptor = ArgumentCaptor.forClass(Refund.class);
        verify(refundRepository, times(2)).save(refundCaptor.capture());
        assertThat(refundCaptor.getValue().getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void applyRefundResult_success_setsOrderRefund_andCancelsRebates() {
        Refund refund = Refund.create(ORDER_ID, "RF-1", AMOUNT);
        when(refundRepository.findByRefundNo("RF-1")).thenReturn(Optional.of(refund));
        Order order = paidOrder(LocalDateTime.now().minusHours(1));
        order.setStatus(OrderStatus.REFUNDING);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(rebateService.cancelForRefund(ORDER_ID)).thenReturn(2);

        service.applyRefundResult(new WxRefundClient.RefundCallbackResult("RF-1", "wx-refund-99", true));

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.SUCCESS);
        assertThat(refund.getWxRefundId()).isEqualTo("wx-refund-99");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUND);
        verify(rebateService).cancelForRefund(ORDER_ID);
    }

    @Test
    void applyRefundResult_alreadySuccess_isNoOp() {
        Refund refund = Refund.create(ORDER_ID, "RF-1", AMOUNT);
        refund.markSuccess("wx-already");
        when(refundRepository.findByRefundNo("RF-1")).thenReturn(Optional.of(refund));

        service.applyRefundResult(new WxRefundClient.RefundCallbackResult("RF-1", "wx-new", true));

        // 幂等：不再触发订单 / 返利变更
        verify(orderRepository, never()).findById(any());
        verify(rebateService, never()).cancelForRefund(any());
    }

    @Test
    void applyRefundResult_failure_setsOrderBackToPaid() {
        Refund refund = Refund.create(ORDER_ID, "RF-1", AMOUNT);
        when(refundRepository.findByRefundNo("RF-1")).thenReturn(Optional.of(refund));
        Order order = paidOrder(LocalDateTime.now().minusHours(1));
        order.setStatus(OrderStatus.REFUNDING);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        service.applyRefundResult(new WxRefundClient.RefundCallbackResult("RF-1", null, false));

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(rebateService, never()).cancelForRefund(any());
    }
}
