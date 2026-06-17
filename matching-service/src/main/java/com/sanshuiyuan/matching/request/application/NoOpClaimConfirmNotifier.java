package com.sanshuiyuan.matching.request.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 降级实现：仅日志，不实际推送。
 * 与 {@link WxMsgClaimConfirmNotifier} 互补激活——未配置 {@code matching.notify.cend-base-url}
 * （或显式 =false）时加载本兜底 bean；配置后由 WxMsg 真实推送接管。
 */
@Component
@ConditionalOnProperty(name = "matching.notify.cend-base-url", havingValue = "false", matchIfMissing = true)
public class NoOpClaimConfirmNotifier implements ClaimConfirmNotifier {

    private static final Logger log = LoggerFactory.getLogger(NoOpClaimConfirmNotifier.class);

    @Override
    public void remind(long requestId, Long ownerUserId, Stage stage) {
        log.info("claim 确认提醒[{}] request_id={} owner={}（008 通道接管前为日志降级）",
                stage, requestId, ownerUserId);
    }
}
