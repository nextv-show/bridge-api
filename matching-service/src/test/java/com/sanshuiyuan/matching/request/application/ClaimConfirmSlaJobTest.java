package com.sanshuiyuan.matching.request.application;

import org.junit.jupiter.api.Test;

import static com.sanshuiyuan.matching.request.application.ClaimConfirmSlaJob.Action;
import static com.sanshuiyuan.matching.request.application.ClaimConfirmSlaJob.decide;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** P1-2 SLA 决策纯逻辑单测（remind1=12h, remind2=22h, sla=24h, window=2min）。 */
class ClaimConfirmSlaJobTest {

    private static final int R1 = 12;
    private static final int R2 = 22;
    private static final int SLA = 24;
    private static final long W = 2;

    private static Action at(long elapsedMin) {
        return decide(elapsedMin, R1, R2, SLA, W);
    }

    @Test
    void beforeFirstReminder_none() {
        assertEquals(Action.NONE, at(0));
        assertEquals(Action.NONE, at(11 * 60));
        assertEquals(Action.NONE, at(12 * 60 - 1));
    }

    @Test
    void softReminderWindow() {
        assertEquals(Action.REMIND_SOFT, at(12 * 60));        // 恰好 12h
        assertEquals(Action.REMIND_SOFT, at(12 * 60 + 1));    // 窗口内
        assertEquals(Action.NONE, at(12 * 60 + 2));           // 窗口外（[12h,12h+2min)）
        assertEquals(Action.NONE, at(15 * 60));               // 两节点之间
    }

    @Test
    void finalReminderWindow() {
        assertEquals(Action.REMIND_FINAL, at(22 * 60));
        assertEquals(Action.REMIND_FINAL, at(22 * 60 + 1));
        assertEquals(Action.NONE, at(22 * 60 + 2));
        assertEquals(Action.NONE, at(23 * 60));               // 预警后、SLA 前
    }

    @Test
    void releaseAtSla() {
        assertEquals(Action.RELEASE, at(24 * 60));            // 恰好 24h
        assertEquals(Action.RELEASE, at(24 * 60 + 30));
        assertEquals(Action.RELEASE, at(72 * 60));            // 远超
    }

    @Test
    void releaseTakesPriorityOverWindows() {
        // 极端配置：sla 与 remind2 重叠时，RELEASE 优先。
        assertEquals(Action.RELEASE, decide(22 * 60, R1, 22, 22, W));
    }
}
