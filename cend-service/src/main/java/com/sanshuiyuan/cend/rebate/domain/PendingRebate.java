package com.sanshuiyuan.cend.rebate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 冷静期返利冻结记录（{@code h5_db.pending_rebates}，011-rebate-freeze）。
 *
 * <p>支付成功后按订单快照的 L1/L2 受益人各冻结一条；冷静期满解冻为已确认，退款则取消。
 *
 * <p><b>合规铁律：</b>
 * <ul>
 *   <li>状态单向流转（{@link RebateStatus}），由领域方法 {@link #confirm()} / {@link #cancel} 守卫，无逆向；</li>
 *   <li>FROZEN 期间不对外暴露金额（见展示层）；</li>
 *   <li>仅 L1+L2 两级（{@link RebateLevel}），严禁 L3+。</li>
 * </ul>
 */
@Entity
@Table(name = "pending_rebates")
public class PendingRebate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    /** 受益人 H5 user_id（L1=订单 inviter_id，L2=订单 grand_inviter_id）。 */
    @Column(name = "beneficiary_id", nullable = false)
    private Long beneficiaryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 2)
    private RebateLevel level;

    /** 返利金额（分）。分账算法待运营确认，当前为配置占位值。 */
    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RebateStatus status;

    @Column(name = "frozen_at", nullable = false)
    private LocalDateTime frozenAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_reason", length = 32)
    private CancelReason cancelReason;

    protected PendingRebate() {
    }

    /**
     * 冻结一条返利（支付成功时调用）。初始状态 FROZEN，frozen_at = 当前时刻。
     * amountCents 为 null 时按 0 占位（分账算法待运营确认）。
     */
    public static PendingRebate freeze(Long orderId, Long beneficiaryId,
                                       RebateLevel level, Long amountCents) {
        PendingRebate r = new PendingRebate();
        r.orderId = orderId;
        r.beneficiaryId = beneficiaryId;
        r.level = level;
        r.amountCents = amountCents == null ? 0L : amountCents;
        r.status = RebateStatus.FROZEN;
        r.frozenAt = LocalDateTime.now();
        return r;
    }

    /** 冷静期满确认：FROZEN→CONFIRMED。非 FROZEN 调用即非法流转。 */
    public void confirm() {
        if (status != RebateStatus.FROZEN) {
            throw new IllegalStateException("仅 FROZEN 返利可确认，当前状态=" + status);
        }
        this.status = RebateStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    /**
     * 退款取消：FROZEN→CANCELLED 或 CONFIRMED→CANCELLED。已取消则禁止重复取消（无逆向）。
     * 调用方按当前状态决定 reason（FROZEN→REFUND_COOLDOWN，CONFIRMED→REFUND_POST_COOLDOWN）。
     */
    public void cancel(CancelReason reason) {
        if (status == RebateStatus.CANCELLED) {
            throw new IllegalStateException("返利已取消，不可重复取消");
        }
        this.status = RebateStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
        this.cancelReason = reason;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public Long getBeneficiaryId() { return beneficiaryId; }
    public RebateLevel getLevel() { return level; }
    public Long getAmountCents() { return amountCents; }
    public RebateStatus getStatus() { return status; }
    public LocalDateTime getFrozenAt() { return frozenAt; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public CancelReason getCancelReason() { return cancelReason; }
}
