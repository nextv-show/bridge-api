package com.sanshuiyuan.h5.checkout.application;

import com.sanshuiyuan.h5.checkout.api.dto.InvoiceDto;
import com.sanshuiyuan.h5.checkout.domain.H5Order;
import com.sanshuiyuan.h5.checkout.domain.Invoice;
import com.sanshuiyuan.h5.checkout.domain.InvoiceStatus;
import com.sanshuiyuan.h5.checkout.domain.OrderStatus;
import com.sanshuiyuan.h5.checkout.infra.repository.H5OrderRepository;
import com.sanshuiyuan.h5.checkout.infra.repository.InvoiceRepository;
import com.sanshuiyuan.h5.common.BizException;
import com.sanshuiyuan.h5.common.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    @Mock InvoiceRepository invoiceRepo;
    @Mock H5OrderRepository orderRepo;

    private InvoiceService createService() {
        return new InvoiceService(invoiceRepo, orderRepo);
    }

    // ─── helpers ───

    private H5Order buildOrder(Long id, String openid) {
        H5Order order = H5Order.create("ORD" + id, openid, "spec-1", "MODEL-X",
                680000L, "WX_JSAPI");
        setField(order, "id", id);
        order.markPaid("wx-txn-123", "SN-" + id, LocalDateTime.now().plusHours(24));
        return order;
    }

    private Invoice issuedInvoice(Long orderId, String downloadUrl) {
        Invoice inv = Invoice.createForOrder(orderId);
        inv.markIssued(downloadUrl);
        return inv;
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

    // ─── 场景 1: 已开发票返回 ISSUED + downloadUrl ───

    @Test
    void getInvoice_alreadyIssued_returnsIssuedWithUrl() {
        H5Order order = buildOrder(1L, "user-A");
        Invoice invoice = issuedInvoice(1L, "https://cdn.example.com/invoice-001.pdf");

        when(orderRepo.findById(1L)).thenReturn(Optional.of(order));
        when(invoiceRepo.findByOrderId(1L)).thenReturn(Optional.of(invoice));

        InvoiceDto result = createService().getInvoice(1L, "user-A");

        assertThat(result.status()).isEqualTo("issued");
        assertThat(result.downloadUrl()).isEqualTo("https://cdn.example.com/invoice-001.pdf");

        // Should not save a new invoice
        verify(invoiceRepo, never()).save(any());
    }

    // ─── 场景 2: 未开发票返回 ISSUING ───

    @Test
    void getInvoice_notYetIssued_returnsIssuing() {
        H5Order order = buildOrder(2L, "user-B");
        Invoice issuingInvoice = Invoice.createForOrder(2L);

        when(orderRepo.findById(2L)).thenReturn(Optional.of(order));
        when(invoiceRepo.findByOrderId(2L)).thenReturn(Optional.of(issuingInvoice));

        InvoiceDto result = createService().getInvoice(2L, "user-B");

        assertThat(result.status()).isEqualTo("issuing");
        assertThat(result.downloadUrl()).isNull();
    }

    // ─── 场景 3: 自动创建发票记录（首次查询时） ───

    @Test
    void getInvoice_noRecordYet_autoCreatesAndReturnsIssuing() {
        H5Order order = buildOrder(3L, "user-C");

        when(orderRepo.findById(3L)).thenReturn(Optional.of(order));
        when(invoiceRepo.findByOrderId(3L)).thenReturn(Optional.empty());
        when(invoiceRepo.save(any(Invoice.class))).thenAnswer(answer -> {
            Invoice inv = answer.getArgument(0);
            setField(inv, "id", 100L);
            return inv;
        });

        InvoiceDto result = createService().getInvoice(3L, "user-C");

        assertThat(result.status()).isEqualTo("issuing");
        assertThat(result.downloadUrl()).isNull();

        // Verify invoice was auto-created for this orderId
        ArgumentCaptor<Invoice> captor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepo).save(captor.capture());
        Invoice saved = captor.getValue();
        assertThat(saved.getOrderId()).isEqualTo(3L);
        assertThat(saved.getStatus()).isEqualTo(InvoiceStatus.ISSUING);
    }

    // ─── 额外: 非本人订单 ───

    @Test
    void getInvoice_notOwner_throws403() {
        H5Order order = buildOrder(4L, "user-D");

        when(orderRepo.findById(4L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> createService().getInvoice(4L, "attacker"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).errorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));

        verify(invoiceRepo, never()).save(any());
    }

    // ─── 额外: 订单不存在 ───

    @Test
    void getInvoice_orderNotFound_throws404() {
        when(orderRepo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> createService().getInvoice(999L, "anyone"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).errorCode())
                        .isEqualTo(ErrorCode.ORDER_NOT_FOUND));
    }
}
