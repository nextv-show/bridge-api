package com.sanshuiyuan.admin.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 用户花名册对账（reconcile）：周期性从 user-service 拉取真实注册用户并 upsert 到 admin
 * {@code users} 表，复用 {@link AdminUserService#syncFromUserService}（按 user_id 幂等，
 * 仅补 nickname/avatar/openid，保留全部 admin 托管字段）。
 *
 * <p><b>为什么是 pull 而非 user-service push</b>：admin 是下游后台，依赖方向应指向上游核心
 * user-service；周期对账天然补齐任何瞬时失败（含 H5 sync-h5 降级）漏掉的用户，与本仓支付/
 * 退款/ESS 的「主动查兜底」范式一致。登录热路径零改动。
 *
 * <p>系统触发的同步以 {@code adminId=0}（系统哨兵，admin_audit_log.admin_id 无 FK）、
 * operator={@code system-scheduler} 记审计；单次失败只记日志，绝不让调度线程抛出。
 */
@Component
@ConditionalOnProperty(name = "admin.user-sync.enabled", havingValue = "true", matchIfMissing = true)
public class UserSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(UserSyncScheduler.class);

    /** 系统触发同步的审计 adminId 哨兵（admin_audit_log.admin_id 为 BIGINT NOT NULL、无 FK）。 */
    private static final long SYSTEM_ADMIN_ID = 0L;
    private static final String SYSTEM_OPERATOR = "system-scheduler";

    private final AdminUserService adminUserService;

    public UserSyncScheduler(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    /**
     * 默认每 10min 一次、启动后延迟 60s 首跑（避开启动期与 user-service 尚未就绪窗口）。
     * 间隔/开关经 {@code admin.user-sync.*} 配置；fixedDelay 保证上一次跑完才排下一次，避免叠跑。
     */
    @Scheduled(
            fixedDelayString = "${admin.user-sync.interval-ms:600000}",
            initialDelayString = "${admin.user-sync.initial-delay-ms:60000}")
    public void reconcile() {
        try {
            Map<String, Object> r = adminUserService.syncFromUserService(SYSTEM_ADMIN_ID, SYSTEM_OPERATOR);
            // 仅在确有增量时记 info，常态无变更降为 debug，避免日志噪声。
            Object inserted = r.get("inserted");
            Object updated = r.get("updated");
            boolean changed = (inserted instanceof Number n1 && n1.longValue() > 0)
                    || (updated instanceof Number n2 && n2.longValue() > 0);
            if (changed) {
                log.info("用户花名册对账完成：{}", r);
            } else {
                log.debug("用户花名册对账完成（无增量）：{}", r);
            }
        } catch (Exception e) {
            // user-service 不可达/抖动等不得让调度线程崩溃；下一周期自动重试。
            log.error("用户花名册对账失败（下一周期重试）：{}", e.getMessage());
        }
    }
}
