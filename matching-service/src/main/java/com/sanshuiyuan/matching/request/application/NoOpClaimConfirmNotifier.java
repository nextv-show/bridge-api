package com.sanshuiyuan.matching.request.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 008 通道上线前的降级实现：仅日志，不实际推送。 */
@Component
public class NoOpClaimConfirmNotifier implements ClaimConfirmNotifier {

    private static final Logger log = LoggerFactory.getLogger(NoOpClaimConfirmNotifier.class);

    @Override
    public void remind(long requestId, Long ownerUserId, Stage stage) {
        log.info("claim 确认提醒[{}] request_id={} owner={}（008 通道接管前为日志降级）",
                stage, requestId, ownerUserId);
    }
}
