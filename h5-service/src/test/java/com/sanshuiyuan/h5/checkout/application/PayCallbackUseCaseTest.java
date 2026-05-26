package com.sanshuiyuan.h5.checkout.application;

import com.sanshuiyuan.h5.checkout.domain.H5Order;
import com.sanshuiyuan.h5.checkout.domain.OrderStatus;
import com.sanshuiyuan.h5.checkout.domain.PaymentInbox;
import com.sanshuiyuan.h5.checkout.infra.repository.DeviceSpecRepository;
import com.sanshuiyuan.h5.checkout.infra.repository.H5OrderRepository;
import com.sanshuiyuan.h5.checkout.infra.repository.PaymentInboxRepository;
import com.sanshuiyuan.h5.checkout.infra.wxpay.WxPayCallbackVerifier;
import com.sanshuiyuan.h5.rebate.application.RebateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayCallbackUseCaseTest {

    @Mock WxPayCallbackVerifier verifier;
    @Mock PaymentInboxRepository inboxRepo;
    @Mock H5OrderRepository orderRepo;
    @Mock DeviceSpecRepository specRepo;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock RebateService rebateService;

    private PayCallbackUseCase createUseCase() {
        return new PayCallbackUseCase(verifier, inboxRepo, orderRepo, specRepo, eventPublisher, rebateService);
    }

    private void setField(Object target, String field, Object value) {
        try {
            var f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private H5Order pendingOrder() {
        H5Order order = H5Order.create("H5TEST123", "openid1", "home-pro", "BR-H2", 680000L, "WX_JSAPI");
        setField(order, "id", 1L);
        return order;
    }

    private H5Order closedOrder() {
        H5Order order = H5Order.create("H5CLOSED123", "openid1", "home-pro", "BR-H2", 680000L, "WX_JSAPI");
        setField(order, "id", 2L);
        order.close();
        return order;
    }

    @Test
    void handleCallback_validPayment_marksPaidWithSnAndCooldown() {
        WxPayCallbackVerifier.VerifyResult verifyResult = new WxPayCallbackVerifier.VerifyResult(
                true, "wx-txn-001", "H5TEST123", "SUCCESS", "{}"
        );
        when(verifier.verifyAndDecrypt("body", "sig", "ts", "nonce", "serial")).thenReturn(verifyResult);
        when(inboxRepo.save(any(PaymentInbox.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepo.findByOrderNo("H5TEST123")).thenReturn(Optional.of(pendingOrder()));
        when(orderRepo.save(any(H5Order.class))).thenAnswer(inv -> inv.getArgument(0));

        PayCallbackUseCase uc = createUseCase();
        String result = uc.handleCallback("body", "sig", "ts", "nonce", "serial");

        assertThat(result).isEqualTo("SUCCESS");

        ArgumentCaptor<H5Order> orderCaptor = ArgumentCaptor.forClass(H5Order.class);
        verify(orderRepo).save(orderCaptor.capture());
        H5Order saved = orderCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(saved.getWxTransactionId()).isEqualTo("wx-txn-001");
        assertThat(saved.getSn()).startsWith("SN-PENDING-");
        assertThat(saved.getCooldownEndAt()).isNotNull();

        // 011: 支付成功须触发返利冻结（自然流量订单 inviter/grand 为 null，仍调用一次）
        verify(rebateService).freezeForOrder(1L, null, null);
    }

    @Test
    void handleCallback_duplicateCallback_idempotentReturn() {
        WxPayCallbackVerifier.VerifyResult verifyResult = new WxPayCallbackVerifier.VerifyResult(
                true, "wx-txn-001", "H5TEST123", "SUCCESS", "{}"
        );
        when(verifier.verifyAndDecrypt("body", "sig", "ts", "nonce", "serial")).thenReturn(verifyResult);
        when(inboxRepo.save(any(PaymentInbox.class))).thenThrow(new DataIntegrityViolationException("dup"));

        PayCallbackUseCase uc = createUseCase();
        String result = uc.handleCallback("body", "sig", "ts", "nonce", "serial");

        assertThat(result).isEqualTo("SUCCESS");
        // Should NOT look up or modify the order
        verify(orderRepo, never()).findByOrderNo(any());
    }

    @Test
    void handleCallback_closedOrderPayment_logsWarningAndReturnsSuccess() {
        WxPayCallbackVerifier.VerifyResult verifyResult = new WxPayCallbackVerifier.VerifyResult(
                true, "wx-txn-002", "H5CLOSED123", "SUCCESS", "{}"
        );
        when(verifier.verifyAndDecrypt("body", "sig", "ts", "nonce", "serial")).thenReturn(verifyResult);
        when(inboxRepo.save(any(PaymentInbox.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepo.findByOrderNo("H5CLOSED123")).thenReturn(Optional.of(closedOrder()));

        PayCallbackUseCase uc = createUseCase();
        String result = uc.handleCallback("body", "sig", "ts", "nonce", "serial");

        assertThat(result).isEqualTo("SUCCESS");
        // Should NOT update the order (it stays CLOSED)
        verify(orderRepo, never()).save(any());
    }

    @Test
    void handleCallback_invalidSignature_returnsFail() {
        WxPayCallbackVerifier.VerifyResult verifyResult = new WxPayCallbackVerifier.VerifyResult(
                false, null, null, null, null
        );
        when(verifier.verifyAndDecrypt("body", "sig", "ts", "nonce", "serial")).thenReturn(verifyResult);

        PayCallbackUseCase uc = createUseCase();
        String result = uc.handleCallback("body", "sig", "ts", "nonce", "serial");

        assertThat(result).isEqualTo("FAIL");
    }

    @Test
    void handleCallback_orderNotFound_returnsFail() {
        WxPayCallbackVerifier.VerifyResult verifyResult = new WxPayCallbackVerifier.VerifyResult(
                true, "wx-txn-003", "NONEXISTENT", "SUCCESS", "{}"
        );
        when(verifier.verifyAndDecrypt("body", "sig", "ts", "nonce", "serial")).thenReturn(verifyResult);
        when(inboxRepo.save(any(PaymentInbox.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepo.findByOrderNo("NONEXISTENT")).thenReturn(Optional.empty());

        PayCallbackUseCase uc = createUseCase();
        String result = uc.handleCallback("body", "sig", "ts", "nonce", "serial");

        assertThat(result).isEqualTo("FAIL");
    }
}
