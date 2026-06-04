package com.sanshuiyuan.cend.usersync;

import com.sanshuiyuan.cend.infra.client.UserServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * H5/小程序登录并号（sync-h5）的「尝试 + 失败兜底入队」封装。
 *
 * <p>登录路径调 {@link #sync}：先尝试实时并号，成功即了结；失败（user-service 降级返回 null）则入
 * {@link H5UserSyncOutbox} 待 {@link ReconcileH5UserSyncJob} 重试。整个过程绝不抛出，不阻断登录。
 *
 * <p>沿用既有 {@link UserServiceClient#syncH5} 的「openid 绝不入日志」约束：本类仅以 canonicalId
 * （unionid 优先）做幂等键与日志标识，不打印任何微信标识明文。
 */
@Service
public class H5UserSyncService {

    private static final Logger log = LoggerFactory.getLogger(H5UserSyncService.class);

    private final UserServiceClient userServiceClient;
    private final H5UserSyncOutboxRepository outboxRepo;

    public H5UserSyncService(UserServiceClient userServiceClient,
                             H5UserSyncOutboxRepository outboxRepo) {
        this.userServiceClient = userServiceClient;
        this.outboxRepo = outboxRepo;
    }

    /**
     * 登录路径调用：实时并号，失败入队兜底。绝不抛出。
     *
     * @param canonicalId 统一身份键（unionid 优先，否则微信 openid）。
     * @param unionid     微信 unionid（可空）。
     * @param inviterId   解码后的推广者 user_id（可空）。
     */
    public void sync(String canonicalId, String unionid, Long inviterId) {
        try {
            UserServiceClient.SyncH5Result result =
                    userServiceClient.syncH5(canonicalId, unionid, inviterId);
            if (result != null) {
                // 成功：若此前留有失败记录（同一 canonicalId），就地了结。
                outboxRepo.findByCanonicalId(canonicalId).ifPresent(row -> {
                    if (!H5UserSyncOutbox.Status.DONE.name().equals(row.getStatus())) {
                        row.markDone();
                        outboxRepo.save(row);
                    }
                });
                return;
            }
            // 失败（user-service 降级）：入队/刷新待重试。
            enqueuePending(canonicalId, unionid, inviterId);
        } catch (Exception e) {
            // 兜底写库本身异常也不得阻断登录。
            log.warn("sync-h5 兜底入队失败（不影响登录）: {}", e.getMessage());
        }
    }

    private void enqueuePending(String canonicalId, String unionid, Long inviterId) {
        outboxRepo.findByCanonicalId(canonicalId).ifPresentOrElse(
                row -> {
                    row.refreshPending(unionid, inviterId);
                    outboxRepo.save(row);
                },
                () -> outboxRepo.save(H5UserSyncOutbox.pending(canonicalId, unionid, inviterId)));
        log.info("sync-h5 降级，已入队兜底重试 canonicalId={}", canonicalId);
    }

    /**
     * 对账任务调用：重试单条待处理记录。成功置 DONE，失败计数+1（超上限置 GAVE_UP）。
     *
     * @return 是否并号成功。
     */
    boolean retry(H5UserSyncOutbox row, int maxAttempts) {
        UserServiceClient.SyncH5Result result =
                userServiceClient.syncH5(row.getCanonicalId(), row.getUnionid(), row.getInviterId());
        if (result != null) {
            row.markDone();
            outboxRepo.save(row);
            return true;
        }
        row.recordFailure("user-service 不可达/降级", maxAttempts);
        outboxRepo.save(row);
        return false;
    }
}
