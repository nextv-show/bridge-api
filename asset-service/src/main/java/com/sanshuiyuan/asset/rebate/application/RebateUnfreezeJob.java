package com.sanshuiyuan.asset.rebate.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 推荐返利解冻定时任务：周期扫描冷静期已满的 FROZEN 返利并确认为 CONFIRMED。
 *
 * <p>与购机关单任务 {@link com.sanshuiyuan.asset.application.CloseExpiredOrdersJob} 同构：
 * 固定延迟轮询、异常仅记录不中断调度。
 */
@Component
public class RebateUnfreezeJob {

    private static final Logger log = LoggerFactory.getLogger(RebateUnfreezeJob.class);

    private final RebateService rebateService;

    public RebateUnfreezeJob(RebateService rebateService) {
        this.rebateService = rebateService;
    }

    @Scheduled(fixedDelay = 60_000)
    public void unfreezeDue() {
        try {
            rebateService.confirmExpired();
        } catch (Exception e) {
            log.error("推荐返利解冻任务执行失败：{}", e.getMessage(), e);
        }
    }
}
