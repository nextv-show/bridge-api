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
 * 纯 Mockito 单测：待支付充值单超时关单（CloseExpiredWalletRechargeJob）。
 * 验证未支付→关单、关单前查到 SUCCESS→兜底入账不关单、查单失败→仍关单、单笔异常不中断整批。
 */
@ExtendWith(MockitoExtension.class)
class CloseExpiredWalletRechargeJobTest {

    @Mock WalletRechargeRepository rechargeRepo;
    @Mock MpWxPayClient mpWxPayClient;
    @Mock WalletService walletService;

    private CloseExpiredWalletRechargeJob job() {
        return new CloseExpiredWalletRechargeJob(rechargeRepo, mpWxPayClient, walletService);
    }

    /** 用反射造一个指定 id / userId / createdAt 的 PENDING_PAY 充值单（实体无 setter）。 */
    private static WalletRecharge pending(long id, long userId, LocalDateTime createdAt) {
        WalletRecharge r = WalletRecharge.create(userId, 10000L, 0, 0, "WECHAT");
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
    void cancelsExpiredUnpaidRecharge() {
        WalletRecharge r = pending(5L, 1L, LocalDateTime.now().minusHours(30));
        when(rechargeRepo.findByStatusAndCreatedAtBefore(eq(RechargeStatus.PENDING_PAY), any()))
                .thenReturn(List.of(r));
        when(mpWxPayClient.queryOrder("WR0000000005"))
                .thenReturn(new MpWxPayClient.TradeQueryResult("NOTPAY", null, null));

        job().closeExpired();

        verify(walletService).cancelRecharge(eq(1L), eq(5L));
        verify(walletService, never()).markPaidByRecharge(any(), any());
    }

    @Test
    void creditsInsteadOfCancelWhenWeChatReportsSuccess() {
        WalletRecharge r = pending(6L, 2L, LocalDateTime.now().minusHours(30));
        when(rechargeRepo.findByStatusAndCreatedAtBefore(eq(RechargeStatus.PENDING_PAY), any()))
                .thenReturn(List.of(r));
        when(mpWxPayClient.queryOrder("WR0000000006"))
                .thenReturn(new MpWxPayClient.TradeQueryResult("SUCCESS", "4200001234", LocalDateTime.now()));

        job().closeExpired();

        verify(walletService).markPaidByRecharge(eq(6L), eq("4200001234"));
        verify(walletService, never()).cancelRecharge(any(), any());
    }

    @Test
    void cancelsWhenQueryFails() {
        WalletRecharge r = pending(7L, 3L, LocalDateTime.now().minusHours(30));
        when(rechargeRepo.findByStatusAndCreatedAtBefore(eq(RechargeStatus.PENDING_PAY), any()))
                .thenReturn(List.of(r));
        when(mpWxPayClient.queryOrder("WR0000000007")).thenThrow(new RuntimeException("boom"));

        job().closeExpired();

        verify(walletService).cancelRecharge(eq(3L), eq(7L));
    }

    @Test
    void oneFailureDoesNotStopBatch() {
        WalletRecharge bad = pending(8L, 4L, LocalDateTime.now().minusHours(30));
        WalletRecharge good = pending(9L, 5L, LocalDateTime.now().minusHours(30));
        when(rechargeRepo.findByStatusAndCreatedAtBefore(eq(RechargeStatus.PENDING_PAY), any()))
                .thenReturn(List.of(bad, good));
        when(mpWxPayClient.queryOrder("WR0000000008"))
                .thenReturn(new MpWxPayClient.TradeQueryResult("NOTPAY", null, null));
        when(mpWxPayClient.queryOrder("WR0000000009"))
                .thenReturn(new MpWxPayClient.TradeQueryResult("NOTPAY", null, null));
        // 第一笔关单时抛错，不应阻断第二笔
        org.mockito.Mockito.doThrow(new RuntimeException("db down")).when(walletService).cancelRecharge(eq(4L), eq(8L));

        job().closeExpired();

        verify(walletService).cancelRecharge(eq(5L), eq(9L));
    }
}
