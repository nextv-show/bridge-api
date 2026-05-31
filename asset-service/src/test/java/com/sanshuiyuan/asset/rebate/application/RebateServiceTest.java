package com.sanshuiyuan.asset.rebate.application;

import com.sanshuiyuan.asset.rebate.api.dto.RebateSummary;
import com.sanshuiyuan.asset.rebate.domain.CancelReason;
import com.sanshuiyuan.asset.rebate.domain.PendingRebate;
import com.sanshuiyuan.asset.rebate.domain.RebateLevel;
import com.sanshuiyuan.asset.rebate.domain.RebateStatus;
import com.sanshuiyuan.asset.rebate.infra.repository.PendingRebateRepository;
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
 * 推荐返利领域服务全路径（纯 Mockito，无 Docker）：
 * 冻结（按人封顶一次 / 仅 L1L2 / 自然流量不冻 / 金额取 SKU 快照）、解冻确认、退款取消、摘要。
 */
@ExtendWith(MockitoExtension.class)
class RebateServiceTest {

    @Mock PendingRebateRepository repo;

    private RebateProperties props;
    private RebateService svc;

    @BeforeEach
    void setUp() {
        props = new RebateProperties();
        props.setCooldownHours(24L);
        svc = new RebateService(repo, props);
    }

    // ─── 冻结：仅 L1 + L2，金额取 SKU 快照，严禁 L3 ───

    @Test
    void freezeForReferee_bothLevels_createsL1AndL2_withSkuFeeSnapshot() {
        when(repo.findByRefereeId(5L)).thenReturn(List.of());

        // referee=5, L1 inviter=10, L2 grandInviter=20; SKU 固定费率 l1=100 l2=50
        svc.freezeForReferee(1L, 5L, 10L, 20L, 100L, 50L);

        ArgumentCaptor<PendingRebate> captor = ArgumentCaptor.forClass(PendingRebate.class);
        verify(repo, times(2)).save(captor.capture());

        List<PendingRebate> saved = captor.getAllValues();
        assertThat(saved).allMatch(r -> r.getStatus() == RebateStatus.FROZEN);
        assertThat(saved).allMatch(r -> r.getRefereeId().equals(5L));
        assertThat(saved).allMatch(r -> r.getOrderId().equals(1L));
        // L1：金额 = SKU l1 快照
        assertThat(saved).anySatisfy(r -> {
            assertThat(r.getLevel()).isEqualTo(RebateLevel.L1);
            assertThat(r.getBeneficiaryId()).isEqualTo(10L);
            assertThat(r.getAmountCents()).isEqualTo(100L);
        });
        // L2：金额 = SKU l2 快照
        assertThat(saved).anySatisfy(r -> {
            assertThat(r.getLevel()).isEqualTo(RebateLevel.L2);
            assertThat(r.getBeneficiaryId()).isEqualTo(20L);
            assertThat(r.getAmountCents()).isEqualTo(50L);
        });
        // 严禁 L3+：至多两条，层级仅 L1/L2
        assertThat(saved).hasSize(2);
        assertThat(saved).extracting(PendingRebate::getLevel)
                .containsExactlyInAnyOrder(RebateLevel.L1, RebateLevel.L2);
    }

    @Test
    void freezeForReferee_naturalTraffic_noRecords() {
        when(repo.findByRefereeId(5L)).thenReturn(List.of());

        svc.freezeForReferee(1L, 5L, null, null, 100L, 50L);

        verify(repo, never()).save(any());
    }

    @Test
    void freezeForReferee_onlyL1_whenGrandNull() {
        when(repo.findByRefereeId(5L)).thenReturn(List.of());

        svc.freezeForReferee(1L, 5L, 10L, null, 100L, 50L);

        ArgumentCaptor<PendingRebate> captor = ArgumentCaptor.forClass(PendingRebate.class);
        verify(repo, times(1)).save(captor.capture());
        assertThat(captor.getValue().getLevel()).isEqualTo(RebateLevel.L1);
        assertThat(captor.getValue().getAmountCents()).isEqualTo(100L);
    }

    /**
     * 「每人仅一次」核心：同一被推荐人已有 L1+L2 记录（哪怕来自旧订单 99），
     * 再下新单（订单 2）也不应再冻结任何记录。
     */
    @Test
    void freezeForReferee_cappedPerReferee_skipsWhenRefereeAlreadyHasBothLevels() {
        when(repo.findByRefereeId(5L)).thenReturn(List.of(
                PendingRebate.freeze(99L, 5L, 10L, RebateLevel.L1, 100L),
                PendingRebate.freeze(99L, 5L, 20L, RebateLevel.L2, 50L)));

        // 新订单 2，同一被推荐人 5，关系链不变
        svc.freezeForReferee(2L, 5L, 10L, 20L, 100L, 50L);

        verify(repo, never()).save(any());
    }

    @Test
    void freezeForReferee_cappedPerReferee_onlyFreezesMissingLevel() {
        // 该被推荐人已有 L1（旧订单），新单只补 L2
        when(repo.findByRefereeId(5L)).thenReturn(List.of(
                PendingRebate.freeze(99L, 5L, 10L, RebateLevel.L1, 100L)));

        svc.freezeForReferee(2L, 5L, 10L, 20L, 100L, 50L);

        ArgumentCaptor<PendingRebate> captor = ArgumentCaptor.forClass(PendingRebate.class);
        verify(repo, times(1)).save(captor.capture());
        assertThat(captor.getValue().getLevel()).isEqualTo(RebateLevel.L2);
        assertThat(captor.getValue().getBeneficiaryId()).isEqualTo(20L);
    }

    // ─── 解冻确认：FROZEN → CONFIRMED ───

    @Test
    void confirmExpired_confirmsDueFrozenRebates() {
        PendingRebate a = PendingRebate.freeze(1L, 5L, 10L, RebateLevel.L1, 100L);
        PendingRebate b = PendingRebate.freeze(2L, 6L, 20L, RebateLevel.L2, 50L);
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
        PendingRebate frozen = PendingRebate.freeze(1L, 5L, 10L, RebateLevel.L1, 100L);
        PendingRebate confirmed = PendingRebate.freeze(1L, 5L, 20L, RebateLevel.L2, 50L);
        confirmed.confirm();
        PendingRebate alreadyCancelled = PendingRebate.freeze(1L, 6L, 30L, RebateLevel.L1, 100L);
        alreadyCancelled.cancel(CancelReason.REFUND_COOLDOWN);

        when(repo.findByOrderId(1L)).thenReturn(List.of(frozen, confirmed, alreadyCancelled));

        int n = svc.cancelForRefund(1L);

        assertThat(n).isEqualTo(2); // 已取消的不计入
        assertThat(frozen.getStatus()).isEqualTo(RebateStatus.CANCELLED);
        assertThat(frozen.getCancelReason()).isEqualTo(CancelReason.REFUND_COOLDOWN);
        assertThat(confirmed.getStatus()).isEqualTo(RebateStatus.CANCELLED);
        assertThat(confirmed.getCancelReason()).isEqualTo(CancelReason.REFUND_POST_COOLDOWN);
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
        PendingRebate c1 = PendingRebate.freeze(1L, 5L, 10L, RebateLevel.L1, 100L);
        c1.confirm();
        PendingRebate c2 = PendingRebate.freeze(2L, 6L, 10L, RebateLevel.L2, 50L);
        c2.confirm();
        PendingRebate frozen = PendingRebate.freeze(3L, 7L, 10L, RebateLevel.L1, 9999L); // 不计入总额
        PendingRebate cancelled = PendingRebate.freeze(4L, 8L, 10L, RebateLevel.L1, 100L);
        cancelled.cancel(CancelReason.REFUND_COOLDOWN);

        when(repo.findByBeneficiaryIdOrderByFrozenAtDesc(10L))
                .thenReturn(List.of(c1, c2, frozen, cancelled));

        RebateSummary summary = svc.summarize(10L);

        assertThat(summary.confirmedTotalCents()).isEqualTo(150L); // 100 + 50，冻结的 9999 不计入
        assertThat(summary.frozenCount()).isEqualTo(1L);
        assertThat(summary.cancelledCount()).isEqualTo(1L);
    }
}
