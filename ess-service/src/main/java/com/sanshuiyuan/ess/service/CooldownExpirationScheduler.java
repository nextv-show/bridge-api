package com.sanshuiyuan.ess.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 冷静期过期定时任务。
 * <p>
 * 每分钟检查并标记过期的冷静期记录，通知订单状态变更。
 */
@Service
public class CooldownExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(CooldownExpirationScheduler.class);

    private final CooldownService cooldownService;
    private final CooldownOrderNotifier orderNotifier;

    public CooldownExpirationScheduler(CooldownService cooldownService,
                                        CooldownOrderNotifier orderNotifier) {
        this.cooldownService = cooldownService;
        this.orderNotifier = orderNotifier;
    }

    /**
     * 每分钟执行一次：标记过期冷静期 + 通知订单状态变更。
     */
    @Scheduled(fixedRate = 60_000)
    public void checkAndMarkExpiredCooldowns() {
        try {
            int count = cooldownService.markExpiredCooldowns();
            if (count > 0) {
                log.info("冷静期过期检查完成，标记 {} 条过期记录", count);
                // 通知订单状态变更
                orderNotifier.notifyCooldownExpiredBatch();
            }
        } catch (Exception e) {
            log.error("冷静期过期检查异常: {}", e.getMessage(), e);
        }
    }
}
