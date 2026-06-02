package com.sanshuiyuan.asset.application;

import com.sanshuiyuan.asset.domain.RechargeStatus;
import com.sanshuiyuan.asset.domain.WalletRecharge;
import com.sanshuiyuan.asset.infra.repository.WalletRechargeRepository;
import com.sanshuiyuan.asset.infra.wxpay.MpWxPayClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 纯 Mockito 单测：钱包充值主动查单兜底（ReconcileWalletRechargeJob）。
 * 验证查到 SUCCESS 才入账、非 SUCCESS 不入账、超窗/太新跳过、单笔异常不中断整批。
 */
@ExtendWith(MockitoExtension.class)
class ReconcileWalletRechargeJobTest {

    @Mock WalletRechargeRepository rechargeRepo;
    @Mock MpWxPayClient mpWxPayClient;
    @Mock WalletService walletService;

    private ReconcileWalletRechargeJob job() {
        return new ReconcileWalletRechargeJob(rechargeRepo, mpWxPayClient, walletService);
    }

    /** 用反射造一个指定 id / createdAt 的 PENDING_PAY 充值单（实体无 setter）。 */
    private static WalletRecharge pending(long id, LocalDateTime createdAt) {
        WalletRecharge r = WalletRecharge.create(1L, 10000L, 0, 0, "WECHAT");
        set(r, "id", id);
        set(r, "createdAt", createdAt);
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
    void creditsWhenWeChatReportsSuccess() {
        WalletRecharge r = pending(5L, LocalDateTime.now().minusMinutes(5));
        when(rechargeRepo.findByStatus(RechargeStatus.PENDING_PAY)).thenReturn(List.of(r));
        when(mpWxPayClient.queryOrder("WR0000000005"))
                .thenReturn(new MpWxPayClient.TradeQueryResult("SUCCESS", "4200001234", LocalDateTime.now()));

        job().reconcile();

        verify(walletService).markPaidByRecharge(eq(5L), eq("4200001234"));
    }

    @Test
    void doesNotCreditWhenNotPaid() {
        WalletRecharge r = pending(7L, LocalDateTime.now().minusMinutes(5));
        when(rechargeRepo.findByStatus(RechargeStatus.PENDING_PAY)).thenReturn(List.of(r));
        when(mpWxPayClient.queryOrder("WR0000000007"))
                .thenReturn(new MpWxPayClient.TradeQueryResult("NOTPAY", null, null));

        job().reconcile();

        verify(walletService, never()).markPaidByRecharge(any(), any());
    }

    @Test
    void skipsTooRecentAndOutOfWindow() {
        WalletRecharge tooNew = pending(1L, LocalDateTime.now().minusSeconds(3));
        WalletRecharge tooOld = pending(2L, LocalDateTime.now().minusHours(30));
        when(rechargeRepo.findByStatus(RechargeStatus.PENDING_PAY)).thenReturn(List.of(tooNew, tooOld));

        job().reconcile();

        verify(mpWxPayClient, never()).queryOrder(any());
        verify(walletService, never()).markPaidByRecharge(any(), any());
    }

    @Test
    void oneFailureDoesNotStopBatch() {
        WalletRecharge bad = pending(3L, LocalDateTime.now().minusMinutes(5));
        WalletRecharge good = pending(4L, LocalDateTime.now().minusMinutes(5));
        when(rechargeRepo.findByStatus(RechargeStatus.PENDING_PAY)).thenReturn(List.of(bad, good));
        when(mpWxPayClient.queryOrder("WR0000000003")).thenThrow(new RuntimeException("boom"));
        when(mpWxPayClient.queryOrder("WR0000000004"))
                .thenReturn(new MpWxPayClient.TradeQueryResult("SUCCESS", "4200005678", LocalDateTime.now()));

        job().reconcile();

        verify(walletService).markPaidByRecharge(eq(4L), eq("4200005678"));
    }
}
