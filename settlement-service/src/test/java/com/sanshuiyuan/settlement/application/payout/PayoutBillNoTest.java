package com.sanshuiyuan.settlement.application.payout;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PayoutBillNoTest {

    @Test
    void smallIdsStillMeetWxMinLength() {
        // 历史 bug：orderId=1/splitId=1 生成 "W1S1"（4 字符），被微信以
        // PARAM_ERROR(字符数 4 < 最小值) 拒绝。零填充后必须满足长度 6–32。
        String billNo = PayoutBillNo.of(1, 1);
        assertThat(billNo).hasSize(18);
        assertThat(billNo.length()).isBetween(6, 32);
        assertThat(billNo).matches("[0-9a-zA-Z]+");
    }

    @Test
    void isDeterministicAndUnique() {
        assertThat(PayoutBillNo.of(1, 1)).isEqualTo(PayoutBillNo.of(1, 1));
        assertThat(PayoutBillNo.of(1, 2)).isNotEqualTo(PayoutBillNo.of(1, 1));
        assertThat(PayoutBillNo.of(2, 1)).isNotEqualTo(PayoutBillNo.of(1, 1));
    }

    @Test
    void realisticLargeIdsStayWithinMaxLength() {
        // 10 位 orderId（~百亿）+ 6 位 splitId 远超业务规模，仍 ≤32。
        assertThat(PayoutBillNo.of(9_999_999_999L, 999_999L).length()).isLessThanOrEqualTo(32);
    }
}
