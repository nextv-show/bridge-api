package com.sanshuiyuan.cend.rebate.api.dto;

import com.sanshuiyuan.cend.rebate.domain.CancelReason;
import com.sanshuiyuan.cend.rebate.domain.PendingRebate;
import com.sanshuiyuan.cend.rebate.domain.RebateLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T11.9 金额可见性合规：仅 CONFIRMED 暴露金额，FROZEN/CANCELLED 一律 null。
 */
class PendingRebateItemTest {

    @Test
    void frozen_hidesAmount() {
        PendingRebate r = PendingRebate.freeze(1L, 10L, RebateLevel.L1, 99900L);

        PendingRebateItem item = PendingRebateItem.from(r);

        assertThat(item.status()).isEqualTo("FROZEN");
        assertThat(item.amountCents()).isNull();   // 冷静期中不显金额
        assertThat(item.orderId()).isEqualTo(1L);   // 仅状态 + 订单引用
        assertThat(item.level()).isEqualTo("L1");
        assertThat(item.frozenAt()).isNotNull();
    }

    @Test
    void confirmed_revealsAmount() {
        PendingRebate r = PendingRebate.freeze(1L, 10L, RebateLevel.L1, 99900L);
        r.confirm();

        PendingRebateItem item = PendingRebateItem.from(r);

        assertThat(item.status()).isEqualTo("CONFIRMED");
        assertThat(item.amountCents()).isEqualTo(99900L);
        assertThat(item.confirmedAt()).isNotNull();
    }

    @Test
    void cancelled_hidesAmount() {
        PendingRebate r = PendingRebate.freeze(1L, 10L, RebateLevel.L1, 99900L);
        r.cancel(CancelReason.REFUND_COOLDOWN);

        PendingRebateItem item = PendingRebateItem.from(r);

        assertThat(item.status()).isEqualTo("CANCELLED");
        assertThat(item.amountCents()).isNull();
    }
}
