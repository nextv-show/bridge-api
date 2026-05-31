package com.sanshuiyuan.asset.rebate.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 推荐返利状态机全路径 + 单向流转守卫。
 */
class PendingRebateTest {

    @Test
    void freeze_initialState_isFrozenWithTimestampAndReferee() {
        PendingRebate r = PendingRebate.freeze(1L, 5L, 10L, RebateLevel.L1, 100L);

        assertThat(r.getStatus()).isEqualTo(RebateStatus.FROZEN);
        assertThat(r.getOrderId()).isEqualTo(1L);
        assertThat(r.getRefereeId()).isEqualTo(5L);
        assertThat(r.getBeneficiaryId()).isEqualTo(10L);
        assertThat(r.getLevel()).isEqualTo(RebateLevel.L1);
        assertThat(r.getAmountCents()).isEqualTo(100L);
        assertThat(r.getFrozenAt()).isNotNull();
        assertThat(r.getConfirmedAt()).isNull();
        assertThat(r.getCancelledAt()).isNull();
        assertThat(r.getCancelReason()).isNull();
    }

    @Test
    void freeze_nullAmount_defaultsToZero() {
        PendingRebate r = PendingRebate.freeze(1L, 5L, 10L, RebateLevel.L2, null);
        assertThat(r.getAmountCents()).isEqualTo(0L);
    }

    // ─── FROZEN → CONFIRMED ───

    @Test
    void confirm_fromFrozen_transitionsToConfirmed() {
        PendingRebate r = PendingRebate.freeze(1L, 5L, 10L, RebateLevel.L1, 100L);

        r.confirm();

        assertThat(r.getStatus()).isEqualTo(RebateStatus.CONFIRMED);
        assertThat(r.getConfirmedAt()).isNotNull();
    }

    @Test
    void confirm_alreadyConfirmed_throws() {
        PendingRebate r = PendingRebate.freeze(1L, 5L, 10L, RebateLevel.L1, 100L);
        r.confirm();

        assertThatThrownBy(r::confirm).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void confirm_afterCancel_throws_noReverse() {
        PendingRebate r = PendingRebate.freeze(1L, 5L, 10L, RebateLevel.L1, 100L);
        r.cancel(CancelReason.REFUND_COOLDOWN);

        assertThatThrownBy(r::confirm).isInstanceOf(IllegalStateException.class);
    }

    // ─── FROZEN → CANCELLED ───

    @Test
    void cancel_fromFrozen_cooldownReason() {
        PendingRebate r = PendingRebate.freeze(1L, 5L, 10L, RebateLevel.L1, 100L);

        r.cancel(CancelReason.REFUND_COOLDOWN);

        assertThat(r.getStatus()).isEqualTo(RebateStatus.CANCELLED);
        assertThat(r.getCancelReason()).isEqualTo(CancelReason.REFUND_COOLDOWN);
        assertThat(r.getCancelledAt()).isNotNull();
    }

    // ─── CONFIRMED → CANCELLED ───

    @Test
    void cancel_fromConfirmed_postCooldownReason() {
        PendingRebate r = PendingRebate.freeze(1L, 5L, 10L, RebateLevel.L1, 100L);
        r.confirm();

        r.cancel(CancelReason.REFUND_POST_COOLDOWN);

        assertThat(r.getStatus()).isEqualTo(RebateStatus.CANCELLED);
        assertThat(r.getCancelReason()).isEqualTo(CancelReason.REFUND_POST_COOLDOWN);
    }

    @Test
    void cancel_alreadyCancelled_throws() {
        PendingRebate r = PendingRebate.freeze(1L, 5L, 10L, RebateLevel.L1, 100L);
        r.cancel(CancelReason.REFUND_COOLDOWN);

        assertThatThrownBy(() -> r.cancel(CancelReason.REFUND_POST_COOLDOWN))
                .isInstanceOf(IllegalStateException.class);
    }
}
