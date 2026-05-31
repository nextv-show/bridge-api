package com.sanshuiyuan.asset.rebate.domain;

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
 * 推荐返利冻结记录（{@code asset_db.pending_rebates}）—— 一次性实物销售介绍费。
 *
 * <p>被推荐人购机订单支付成功后，按 user-service 返回的 L1/L2 受益人各冻结一条（金额为下单时刻
 * SKU 固定费率的快照）；冷静期满解冻为已确认，购机退款则取消。
 *
 * <p><b>合规铁律：</b>
 * <ul>
 *   <li>每个被推荐人「仅一次」：封顶键为 {@code (referee_id, level)}，与 H5「按订单」不同——
 *       同一被推荐人无论下几单，同一层级至多一条返利记录；</li>
 *   <li>金额按机型（SKU）固定，进记录时快照，<b>严禁</b>做成购机款百分比；</li>
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

    /** 触发返利的购机订单 id（orders.id），仅记录触发来源。 */
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    /** 被推荐人 user_id（= 购机下单人 orders.user_id），「每人仅一次」封顶的主体。 */
    @Column(name = "referee_id", nullable = false)
    private Long refereeId;

    /** 受益人 user_id（L1=被推荐人的直接邀请人，L2=间接邀请人）。 */
    @Column(name = "beneficiary_id", nullable = false)
    private Long beneficiaryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 2)
    private RebateLevel level;

    /** 介绍费金额（分）。下单时刻按 SKU 固定费率快照，之后改费率不影响本记录。 */
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
     * 冻结一条返利（购机支付成功时调用）。初始状态 FROZEN，frozen_at = 当前时刻。
     * amountCents 为 null 时按 0 占位。
     *
     * @param orderId       触发的购机订单 id
     * @param refereeId     被推荐人 user_id（购机下单人）
     * @param beneficiaryId 受益人 user_id（L1 邀请人 / L2 间接邀请人）
     * @param level         受益层级 L1/L2
     * @param amountCents   SKU 固定介绍费快照（分）
     */
    public static PendingRebate freeze(Long orderId, Long refereeId, Long beneficiaryId,
                                       RebateLevel level, Long amountCents) {
        PendingRebate r = new PendingRebate();
        r.orderId = orderId;
        r.refereeId = refereeId;
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
    public Long getRefereeId() { return refereeId; }
    public Long getBeneficiaryId() { return beneficiaryId; }
    public RebateLevel getLevel() { return level; }
    public Long getAmountCents() { return amountCents; }
    public RebateStatus getStatus() { return status; }
    public LocalDateTime getFrozenAt() { return frozenAt; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public CancelReason getCancelReason() { return cancelReason; }
}
