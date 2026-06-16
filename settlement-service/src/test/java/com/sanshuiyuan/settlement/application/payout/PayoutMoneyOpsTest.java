package com.sanshuiyuan.settlement.application.payout;

import com.sanshuiyuan.settlement.domain.OwnerWallet;
import com.sanshuiyuan.settlement.domain.PaymentChannel;
import com.sanshuiyuan.settlement.domain.SplitKind;
import com.sanshuiyuan.settlement.domain.SplitStatus;
import com.sanshuiyuan.settlement.domain.WithdrawalOrder;
import com.sanshuiyuan.settlement.domain.WithdrawalSplit;
import com.sanshuiyuan.settlement.domain.WithdrawalStatus;
import com.sanshuiyuan.settlement.infra.repository.OwnerWalletRepository;
import com.sanshuiyuan.settlement.infra.repository.WalletLedgerRepository;
import com.sanshuiyuan.settlement.infra.repository.WithdrawalOrderRepository;
import com.sanshuiyuan.settlement.infra.repository.WithdrawalSplitRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayoutMoneyOpsTest {

    private final WithdrawalOrderRepository orderRepo = mock(WithdrawalOrderRepository.class);
    private final WithdrawalSplitRepository splitRepo = mock(WithdrawalSplitRepository.class);
    private final OwnerWalletRepository walletRepo = mock(OwnerWalletRepository.class);
    private final WalletLedgerRepository ledgerRepo = mock(WalletLedgerRepository.class);
    private final PayoutMoneyOps ops = new PayoutMoneyOps(orderRepo, splitRepo, walletRepo, ledgerRepo);

    private static final long USER = 7L;
    private static final long ORDER = 1L;
    private static final long GROSS = 1000L;

    private WithdrawalOrder order(WithdrawalStatus status) {
        WithdrawalOrder o = new WithdrawalOrder(USER, GROSS, 20L, 980L, status, "cr1");
        return o;
    }

    private WithdrawalSplit split(SplitStatus status) {
        WithdrawalSplit s = new WithdrawalSplit(ORDER, SplitKind.CASH, 980L, PaymentChannel.WX_MCH_PAYOUT, status, 0);
        return s;
    }

    @Test
    void refundOnFailure_refundsOnce_andIsIdempotent() {
        WithdrawalOrder o = order(WithdrawalStatus.PROCESSING);
        OwnerWallet wallet = new OwnerWallet(USER, 0L, GROSS); // balance 0, frozen 1000
        when(orderRepo.findById(ORDER)).thenReturn(Optional.of(o));
        when(splitRepo.findByOrderId(ORDER)).thenReturn(List.of(split(SplitStatus.PAYING)));
        when(walletRepo.findById(USER)).thenReturn(Optional.of(wallet));

        ops.refundOnFailure(ORDER, "WX_FAIL");

        assertThat(wallet.getBalanceCents()).isEqualTo(GROSS); // 全额退回
        assertThat(wallet.getFrozenCents()).isEqualTo(0L);     // 解冻
        assertThat(o.getStatus()).isEqualTo(WithdrawalStatus.FAILED);
        verify(ledgerRepo, times(1)).save(any());

        // 再次调用：订单已终态 → 不再二次退款
        ops.refundOnFailure(ORDER, "WX_FAIL");
        assertThat(wallet.getBalanceCents()).isEqualTo(GROSS); // 仍是 1000，未被二次加钱
        assertThat(wallet.getFrozenCents()).isEqualTo(0L);
        verify(ledgerRepo, times(1)).save(any());              // 退款流水仍只 1 条
    }

    @Test
    void releaseOnSuccess_releasesFrozenOnce_andIsIdempotent() {
        WithdrawalOrder o = order(WithdrawalStatus.PROCESSING);
        OwnerWallet wallet = new OwnerWallet(USER, 0L, GROSS);
        when(orderRepo.findById(ORDER)).thenReturn(Optional.of(o));
        when(splitRepo.findByOrderId(ORDER)).thenReturn(List.of(split(SplitStatus.PAYING)));
        when(walletRepo.findById(USER)).thenReturn(Optional.of(wallet));

        ops.releaseOnSuccess(ORDER);

        assertThat(wallet.getFrozenCents()).isEqualTo(0L);     // 释放冻结
        assertThat(wallet.getBalanceCents()).isEqualTo(0L);    // 余额不动（钱已转走）
        assertThat(o.getStatus()).isEqualTo(WithdrawalStatus.DONE);

        // 再次调用：已 DONE → no-op，不重复释放
        ops.releaseOnSuccess(ORDER);
        assertThat(wallet.getFrozenCents()).isEqualTo(0L);
    }
}
