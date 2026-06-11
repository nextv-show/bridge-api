package com.sanshuiyuan.asset.api;

import com.sanshuiyuan.asset.application.WalletService;
import com.sanshuiyuan.asset.domain.WalletRecharge;
import com.sanshuiyuan.asset.infra.client.UserServiceClient;
import com.sanshuiyuan.asset.infra.wxpay.MpWxPayClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 纯 Mockito 单测：取消待支付充值单（WalletPayController#cancel）的资损防护。
 * 验证 stub 直接放行、真实支付下「已确定未支付才取消、已支付转入账、状态未知拒绝取消」。
 */
@ExtendWith(MockitoExtension.class)
class WalletPayControllerCancelTest {

    @Mock WalletService walletService;
    @Mock UserServiceClient userServiceClient;
    @Mock MpWxPayClient mpWxPayClient;

    private WalletPayController controller() {
        return new WalletPayController(walletService, userServiceClient, mpWxPayClient);
    }

    private static WalletRecharge pending(long id) {
        WalletRecharge r = WalletRecharge.create(1L, 10000L, 0, 0, "WECHAT");
        set(r, "id", id);
        return r;
    }

    private static void set(Object target, String field, Object value) {
        try {
            Field f = WalletRecharge.class.getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void stubCancelsWithoutQuery() {
        WalletRecharge r = pending(5L);
        when(walletService.getOwnedRecharge(1L, 5L)).thenReturn(r);
        when(mpWxPayClient.isReal()).thenReturn(false);
        WalletRecharge cancelled = pending(5L);
        cancelled.cancel();
        when(walletService.cancelRecharge(1L, 5L)).thenReturn(cancelled);

        ResponseEntity<?> resp = controller().cancel(1L, 5L);

        assertEquals(200, resp.getStatusCode().value());
        verify(mpWxPayClient, never()).queryOrder(any());
        verify(walletService).cancelRecharge(1L, 5L);
    }

    @Test
    void realCancelsWhenConfirmedUnpaid() {
        WalletRecharge r = pending(6L);
        when(walletService.getOwnedRecharge(1L, 6L)).thenReturn(r);
        when(mpWxPayClient.isReal()).thenReturn(true);
        when(mpWxPayClient.queryOrder("WR0000000006"))
                .thenReturn(new MpWxPayClient.TradeQueryResult("NOTPAY", null, null));
        WalletRecharge cancelled = pending(6L);
        cancelled.cancel();
        when(walletService.cancelRecharge(1L, 6L)).thenReturn(cancelled);

        ResponseEntity<?> resp = controller().cancel(1L, 6L);

        assertEquals(200, resp.getStatusCode().value());
        verify(walletService).cancelRecharge(1L, 6L);
    }

    @Test
    void realCreditsWhenAlreadyPaid() {
        WalletRecharge r = pending(7L);
        when(walletService.getOwnedRecharge(1L, 7L)).thenReturn(r);
        when(mpWxPayClient.isReal()).thenReturn(true);
        when(mpWxPayClient.queryOrder("WR0000000007"))
                .thenReturn(new MpWxPayClient.TradeQueryResult("SUCCESS", "4200001234", LocalDateTime.now()));
        WalletRecharge paid = pending(7L);
        paid.markPaid("4200001234");
        when(walletService.markPaidByRecharge(7L, "4200001234")).thenReturn(paid);

        ResponseEntity<?> resp = controller().cancel(1L, 7L);

        assertEquals(200, resp.getStatusCode().value());
        verify(walletService).markPaidByRecharge(7L, "4200001234");
        verify(walletService, never()).cancelRecharge(any(), any());
    }

    @Test
    void realRefusesWhenStateUnknown() {
        // 资损防护：查单失败（QUERY_ERROR）时拒绝取消，返回 409，待用户重试。
        WalletRecharge r = pending(8L);
        when(walletService.getOwnedRecharge(1L, 8L)).thenReturn(r);
        when(mpWxPayClient.isReal()).thenReturn(true);
        when(mpWxPayClient.queryOrder("WR0000000008"))
                .thenReturn(new MpWxPayClient.TradeQueryResult("QUERY_ERROR", null, null));

        ResponseEntity<?> resp = controller().cancel(1L, 8L);

        assertEquals(409, resp.getStatusCode().value());
        verify(walletService, never()).cancelRecharge(any(), any());
        verify(walletService, never()).markPaidByRecharge(any(), any());
    }
}
