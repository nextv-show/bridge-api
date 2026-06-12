package com.sanshuiyuan.matching.request.application;

/**
 * P1-2 接单确认提醒钩子（design §4.1）。
 * 008 消息通道上线前由 {@link NoOpClaimConfirmNotifier} 日志降级；上线后替换为真实推送 + 幂等已发标记。
 */
public interface ClaimConfirmNotifier {

    enum Stage {
        /** T+12h 软提醒。 */
        SOFT,
        /** T+22h 最终预警（即将自动释放）。 */
        FINAL
    }

    void remind(long requestId, Long ownerUserId, Stage stage);
}
