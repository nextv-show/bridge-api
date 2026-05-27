package com.sanshuiyuan.h5.rebate.application;

import com.sanshuiyuan.h5.rebate.api.dto.RebateSummary;
import com.sanshuiyuan.h5.rebate.domain.CancelReason;
import com.sanshuiyuan.h5.rebate.domain.PendingRebate;
import com.sanshuiyuan.h5.rebate.domain.RebateLevel;
import com.sanshuiyuan.h5.rebate.domain.RebateStatus;
import com.sanshuiyuan.h5.rebate.infra.repository.PendingRebateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T11.9 返利领域服务全路径：冻结（仅 L1/L2）/ 解冻确认 / 退款取消 / 摘要。
 */
@ExtendWith(MockitoExtension.class)
class RebateServiceTest {

    @Mock PendingRebateRepository repo;
    @Mock RebateProperties props;
    @Mock com.sanshuiyuan.h5.referral.H5UserRepository userRepo;
    @Mock com.sanshuiyuan.h5.realtime.H5RealtimeBroadcaster realtimeBroadcaster;

    private RebateService svc;

    @BeforeEach
    void setUp() {
        props = new RebateProperties();
        props.setL1AmountCents(100L);
        props.setL2AmountCents(50L);
        props.setCooldownHours(24L);
        svc = new RebateService(repo, props, userRepo, realtimeBroadcaster);
    }

    // ─── 冻结：仅 L1 + L2，严禁 L3 ───

    @Test
    void freezeForOrder_bothLevels_createsL1AndL2() {
        when(repo.findByOrderId(1L)).thenReturn(List.of());

        svc.freezeForOrder(1L, 10L, 20L);

        ArgumentCaptor<PendingRebate> captor = ArgumentCaptor.forClass(PendingRebate.class);
        verify(repo, times(2)).save(captor.capture());

        List<PendingRebate> saved = captor.getAllValues();
        assertThat(saved).allMatch(r -> r.getStatus() == RebateStatus.FROZEN);
        // L1
        assertThat(saved).anySatisfy(r -> {
            assertThat(r.getLevel()).isEqualTo(RebateLevel.L1);
            assertThat(r.getBeneficiaryId()).isEqualTo(10L);
            assertThat(r.getAmountCents()).isEqualTo(100L);
        });
        // L2
        assertThat(saved).anySatisfy(r -> {
            assertThat(r.getLevel()).isEqualTo(RebateLevel.L2);
            assertThat(r.getBeneficiaryId()).isEqualTo(20L);
            assertThat(r.getAmountCents()).isEqualTo(50L);
        });
        // 严禁 L3+：至多两条，且层级仅 L1/L2
        assertThat(saved).hasSize(2);
        assertThat(saved).extracting(PendingRebate::getLevel)
                .containsExactlyInAnyOrder(RebateLevel.L1, RebateLevel.L2);
    }

    @Test
    void freezeForOrder_naturalTraffic_noRecords() {
        when(repo.findByOrderId(1L)).thenReturn(List.of());

        svc.freezeForOrder(1L, null, null);

        verify(repo, never()).save(any());
    }

    @Test
    void freezeForOrder_onlyL1_whenGrandNull() {
        when(repo.findByOrderId(1L)).thenReturn(List.of());

        svc.freezeForOrder(1L, 10L, null);

        ArgumentCaptor<PendingRebate> captor = ArgumentCaptor.forClass(PendingRebate.class);
        verify(repo, times(1)).save(captor.capture());
        assertThat(captor.getValue().getLevel()).isEqualTo(RebateLevel.L1);
    }

    @Test
    void freezeForOrder_idempotent_skipsExistingLevel() {
        // 已存在 L1（重复回调），只应再冻结 L2
        when(repo.findByOrderId(1L)).thenReturn(List.of(
                PendingRebate.freeze(1L, 10L, RebateLevel.L1, 100L)));

        svc.freezeForOrder(1L, 10L, 20L);

        ArgumentCaptor<PendingRebate> captor = ArgumentCaptor.forClass(PendingRebate.class);
        verify(repo, times(1)).save(captor.capture());
        assertThat(captor.getValue().getLevel()).isEqualTo(RebateLevel.L2);
    }

    // ─── 解冻确认：FROZEN → CONFIRMED ───

    @Test
    void confirmExpired_confirmsDueFrozenRebates() {
        PendingRebate a = PendingRebate.freeze(1L, 10L, RebateLevel.L1, 100L);
        PendingRebate b = PendingRebate.freeze(2L, 20L, RebateLevel.L2, 50L);
        when(repo.findByStatusAndFrozenAtBefore(eq(RebateStatus.FROZEN), any()))
                .thenReturn(List.of(a, b));

        int n = svc.confirmExpired();

        assertThat(n).isEqualTo(2);
        assertThat(a.getStatus()).isEqualTo(RebateStatus.CONFIRMED);
        assertThat(b.getStatus()).isEqualTo(RebateStatus.CONFIRMED);
        verify(repo, times(2)).save(any(PendingRebate.class));
    }

    @Test
    void confirmExpired_noneDue_noSaves() {
        when(repo.findByStatusAndFrozenAtBefore(eq(RebateStatus.FROZEN), any()))
                .thenReturn(List.of());

        int n = svc.confirmExpired();

        assertThat(n).isZero();
        verify(repo, never()).save(any());
    }

    // ─── 退款取消：按状态区分原因 ───

    @Test
    void cancelForRefund_frozenAndConfirmed_cancelledWithProperReasons() {
        PendingRebate frozen = PendingRebate.freeze(1L, 10L, RebateLevel.L1, 100L);
        PendingRebate confirmed = PendingRebate.freeze(1L, 20L, RebateLevel.L2, 50L);
        confirmed.confirm();
        PendingRebate alreadyCancelled = PendingRebate.freeze(1L, 30L, RebateLevel.L1, 100L);
        alreadyCancelled.cancel(CancelReason.REFUND_COOLDOWN);

        when(repo.findByOrderId(1L)).thenReturn(List.of(frozen, confirmed, alreadyCancelled));

        int n = svc.cancelForRefund(1L);

        assertThat(n).isEqualTo(2); // 已取消的不计入
        assertThat(frozen.getStatus()).isEqualTo(RebateStatus.CANCELLED);
        assertThat(frozen.getCancelReason()).isEqualTo(CancelReason.REFUND_COOLDOWN);
        assertThat(confirmed.getStatus()).isEqualTo(RebateStatus.CANCELLED);
        assertThat(confirmed.getCancelReason()).isEqualTo(CancelReason.REFUND_POST_COOLDOWN);
        // 已取消的保持原状、不被重复保存
        verify(repo, times(2)).save(any(PendingRebate.class));
    }

    @Test
    void cancelForRefund_noRebates_noop() {
        when(repo.findByOrderId(9L)).thenReturn(List.of());

        int n = svc.cancelForRefund(9L);

        assertThat(n).isZero();
        verify(repo, never()).save(any());
    }

    // ─── 摘要：仅 CONFIRMED 计入总额 ───

    @Test
    void summarize_onlyConfirmedCountedInTotal() {
        PendingRebate c1 = PendingRebate.freeze(1L, 10L, RebateLevel.L1, 100L);
        c1.confirm();
        PendingRebate c2 = PendingRebate.freeze(2L, 10L, RebateLevel.L2, 50L);
        c2.confirm();
        PendingRebate frozen = PendingRebate.freeze(3L, 10L, RebateLevel.L1, 9999L); // 不计入总额
        PendingRebate cancelled = PendingRebate.freeze(4L, 10L, RebateLevel.L1, 100L);
        cancelled.cancel(CancelReason.REFUND_COOLDOWN);

        when(repo.findByBeneficiaryIdOrderByFrozenAtDesc(10L))
                .thenReturn(List.of(c1, c2, frozen, cancelled));

        RebateSummary summary = svc.summarize(10L);

        assertThat(summary.confirmedTotalCents()).isEqualTo(150L); // 100 + 50，冻结的 9999 不计入
        assertThat(summary.frozenCount()).isEqualTo(1L);
        assertThat(summary.cancelledCount()).isEqualTo(1L);
    }
}
