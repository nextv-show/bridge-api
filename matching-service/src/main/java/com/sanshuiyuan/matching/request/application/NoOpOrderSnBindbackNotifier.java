package com.sanshuiyuan.matching.request.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 降级实现：仅日志，不实际回写。
 * 与 {@link CendOrderSnBindbackNotifier} 互补激活——未配置 {@code matching.notify.cend-base-url}
 * （或显式 =false）时加载本兜底 bean；配置后由真实回写接管。
 */
@Component
@ConditionalOnProperty(name = "matching.notify.cend-base-url", havingValue = "false", matchIfMissing = true)
public class NoOpOrderSnBindbackNotifier implements OrderSnBindbackNotifier {

    private static final Logger log = LoggerFactory.getLogger(NoOpOrderSnBindbackNotifier.class);

    @Override
    public void notifyBindSn(long deviceAssetId, String sn) {
        log.debug("SN 回写 device_asset_id={} sn={}（cend-base-url 未配置，日志降级）", deviceAssetId, sn);
    }
}
