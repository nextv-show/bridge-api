package com.sanshuiyuan.ess.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 冷静期记录实体。
 * <p>
 * 24 小时冷静期从支付成功开始计算。
 * 状态流转：ACTIVE → EXPIRED / REVOKED / CANCELLED。
 */
@Entity
@Table(name = "contract_cooldown_records")
public class ContractCooldownRecord {

    /**
     * 冷静期状态枚举。
     */
    public enum CooldownStatus {
        /** 冷静期中 */
        ACTIVE,
        /** 冷静期已过期 */
        EXPIRED,
        /** 已撤销（冷静期内撤回） */
        REVOKED,
        /** 已取消（其他原因） */
        CANCELLED;

        public boolean canTransitionTo(CooldownStatus target) {
            return switch (this) {
                case ACTIVE -> target == EXPIRED || target == REVOKED || target == CANCELLED;
                case EXPIRED, REVOKED, CANCELLED -> false;
            };
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "order_id", nullable = false, length = 64)
    private String orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "cooldown_start_at", nullable = false)
    private LocalDateTime cooldownStartAt;

    @Column(name = "cooldown_end_at", nullable = false)
    private LocalDateTime cooldownEndAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private CooldownStatus status;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoke_reason", length = 512)
    private String revokeReason;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected ContractCooldownRecord() {
    }

    /**
     * 创建冷静期记录。
     *
     * @param contractId      合同 ID
     * @param orderId         订单 ID
     * @param userId          用户 ID
     * @param cooldownStartAt 冷静期开始时间
     * @param cooldownHours   冷静期时长（小时）
     * @return 冷静期记录
     */
    public static ContractCooldownRecord create(Long contractId, String orderId,
                                                 Long userId,
                                                 LocalDateTime cooldownStartAt,
                                                 int cooldownHours) {
        ContractCooldownRecord record = new ContractCooldownRecord();
        record.contractId = contractId;
        record.orderId = orderId;
        record.userId = userId;
        record.cooldownStartAt = cooldownStartAt;
        record.cooldownEndAt = cooldownStartAt.plusHours(cooldownHours);
        record.status = CooldownStatus.ACTIVE;
        return record;
    }

    /**
     * 撤销（冷静期内）。
     */
    public void revoke(String reason) {
        if (!status.canTransitionTo(CooldownStatus.REVOKED)) {
            throw new IllegalStateException(
                    String.format("冷静期状态不允许撤销，当前状态: %s", status));
        }
        this.status = CooldownStatus.REVOKED;
        this.revokedAt = LocalDateTime.now();
        this.revokeReason = reason;
    }

    /**
     * 标记过期。
     */
    public void markExpired() {
        if (!status.canTransitionTo(CooldownStatus.EXPIRED)) {
            return; // 幂等：已过期则跳过
        }
        this.status = CooldownStatus.EXPIRED;
    }

    /**
     * 取消。
     */
    public void cancel() {
        if (!status.canTransitionTo(CooldownStatus.CANCELLED)) {
            return;
        }
        this.status = CooldownStatus.CANCELLED;
    }

    /**
     * 判断冷静期是否已过期。
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(cooldownEndAt);
    }

    /**
     * 获取剩余秒数。
     */
    public long getRemainingSeconds() {
        if (isExpired()) return 0;
        return java.time.Duration.between(LocalDateTime.now(), cooldownEndAt).getSeconds();
    }

    public Long getId() { return id; }
    public Long getContractId() { return contractId; }
    public String getOrderId() { return orderId; }
    public Long getUserId() { return userId; }
    public LocalDateTime getCooldownStartAt() { return cooldownStartAt; }
    public LocalDateTime getCooldownEndAt() { return cooldownEndAt; }
    public CooldownStatus getStatus() { return status; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public String getRevokeReason() { return revokeReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
