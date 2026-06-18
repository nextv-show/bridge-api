package com.sanshuiyuan.matching.request.application;

/**
 * 110 SN 回写钩子：设备阶段推进成功后（履约 / 激活），通知 cend-service 把真实 SN
 * 回写到 h5_orders（替换支付时写入的占位符）。
 *
 * <p>未配置 {@code matching.notify.cend-base-url} 时由 {@link NoOpOrderSnBindbackNotifier}
 * 日志降级；配置后由 {@link CendOrderSnBindbackNotifier} 经 S2S 真实回写。
 * best-effort：实现方保证不抛出异常（内部 try-catch 降级为日志），绝不阻塞履约/激活主流程。
 */
public interface OrderSnBindbackNotifier {

    /**
     * 通知 cend 回写真实 SN。
     *
     * @param deviceAssetId 设备资产 id（cend 侧据此反查 order_no）
     * @param sn            真实 SN；为 {@code null} 时实现方按 deviceAssetId 反查 device_assets.sn
     */
    void notifyBindSn(long deviceAssetId, String sn);
}
