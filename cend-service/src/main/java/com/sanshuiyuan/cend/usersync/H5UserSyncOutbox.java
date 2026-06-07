package com.sanshuiyuan.cend.usersync;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * H5/小程序登录并号（sync-h5）失败兜底记录（cend-service 自有 {@code core_db.h5_user_sync_outbox}）。
 *
 * <p>仅在 {@code userServiceClient.syncH5} 降级失败时入队；{@link ReconcileH5UserSyncJob} 周期重试至成功。
 * 按 {@code canonicalId} 唯一，重复登录失败只刷新同一行（不重复入队）。
 */
@Entity
@Table(name = "h5_user_sync_outbox")
public class H5UserSyncOutbox {

    public enum Status { PENDING, DONE, GAVE_UP }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 登录统一身份键（unionid 优先，否则微信 openid），即传给 sync-h5 的首参；唯一。 */
    @Column(name = "canonical_id", nullable = false, unique = true, length = 64)
    private String canonicalId;

    @Column(length = 64)
    private String unionid;

    /** 解码后的推广者 user_id（自然流量为 null）；重试时原样回放，sync-h5 仅首次创建写关系链。 */
    @Column(name = "inviter_id")
    private Long inviterId;

    @Column(nullable = false, length = 16)
    private String status = Status.PENDING.name();

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "last_error", length = 256)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected H5UserSyncOutbox() {
    }

    static H5UserSyncOutbox pending(String canonicalId, String unionid, Long inviterId) {
        H5UserSyncOutbox o = new H5UserSyncOutbox();
        o.canonicalId = canonicalId;
        o.unionid = unionid;
        o.inviterId = inviterId;
        o.status = Status.PENDING.name();
        return o;
    }

    /** 再次失败：刷新身份快照（非空才覆盖，避免把已有 unionid/inviterId 抹空）、重置为待处理。 */
    void refreshPending(String unionid, Long inviterId) {
        if (unionid != null && !unionid.isBlank()) {
            this.unionid = unionid;
        }
        if (inviterId != null) {
            this.inviterId = inviterId;
        }
        this.status = Status.PENDING.name();
    }

    void markDone() {
        this.status = Status.DONE.name();
        this.lastError = null;
    }

    /** 记一次失败重试：计数+1，超过上限置 GAVE_UP（供告警），否则保持 PENDING。 */
    void recordFailure(String error, int maxAttempts) {
        this.attempts += 1;
        this.lastError = error != null && error.length() > 256 ? error.substring(0, 256) : error;
        if (this.attempts >= maxAttempts) {
            this.status = Status.GAVE_UP.name();
        }
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getCanonicalId() { return canonicalId; }
    public String getUnionid() { return unionid; }
    public Long getInviterId() { return inviterId; }
    public String getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public String getLastError() { return lastError; }
}
