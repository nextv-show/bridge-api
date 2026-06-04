package com.sanshuiyuan.cend.usersync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * H5/小程序并号兜底对账：周期重试 {@link H5UserSyncOutbox} 中的 PENDING 记录，
 * 让登录窗口里 sync-h5 降级漏掉的用户最终并入统一用户体系（再由 admin UserSyncScheduler 拉入花名册）。
 *
 * <p>与本服务支付/退款的「主动查兜底」同范式：单条失败不中断整批，超重试上限置 GAVE_UP 供告警。
 */
@Component
@ConditionalOnProperty(name = "h5.user-sync-reconcile.enabled", havingValue = "true", matchIfMissing = true)
public class ReconcileH5UserSyncJob {

    private static final Logger log = LoggerFactory.getLogger(ReconcileH5UserSyncJob.class);

    /** 单批最多处理的待重试记录数（避免长尾积压时单次跑过久）。 */
    private static final int BATCH_SIZE = 100;
    /** 重试上限：达到后置 GAVE_UP，不再自动重试（需人工/告警介入）。 */
    private static final int MAX_ATTEMPTS = 20;

    private final H5UserSyncOutboxRepository outboxRepo;
    private final H5UserSyncService syncService;

    public ReconcileH5UserSyncJob(H5UserSyncOutboxRepository outboxRepo,
                                  H5UserSyncService syncService) {
        this.outboxRepo = outboxRepo;
        this.syncService = syncService;
    }

    @Scheduled(fixedDelay = 60_000)
    public void reconcile() {
        List<H5UserSyncOutbox> pending = outboxRepo.findByStatusOrderByIdAsc(
                H5UserSyncOutbox.Status.PENDING.name(), PageRequest.of(0, BATCH_SIZE));
        if (pending.isEmpty()) {
            return;
        }
        int ok = 0;
        int fail = 0;
        for (H5UserSyncOutbox row : pending) {
            try {
                if (syncService.retry(row, MAX_ATTEMPTS)) {
                    ok++;
                } else {
                    fail++;
                    if (H5UserSyncOutbox.Status.GAVE_UP.name().equals(row.getStatus())) {
                        log.error("sync-h5 兜底重试超 {} 次仍失败，置 GAVE_UP canonicalId={}",
                                MAX_ATTEMPTS, row.getCanonicalId());
                    }
                }
            } catch (Exception e) {
                fail++;
                log.error("sync-h5 兜底重试异常 canonicalId={}: {}", row.getCanonicalId(), e.getMessage());
            }
        }
        log.info("sync-h5 兜底对账：处理 {} 条，成功 {}，仍失败 {}", pending.size(), ok, fail);
    }
}
