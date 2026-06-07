package com.sanshuiyuan.water.session.domain;

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
 * 用水账单（结算产物，每会话一行）。session_id UNIQUE 保证幂等结算只产生一条账单。
 * chain_status 跟踪上链状态，由 evidence-worker 异步推进。
 */
@Entity
@Table(name = "water_bills")
public class WaterBill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, unique = true)
    private Long sessionId;

    @Column(name = "sn", nullable = false)
    private String sn;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "liters_milli", nullable = false)
    private Long litersMilli;

    @Column(name = "price_per_liter_cents", nullable = false)
    private Integer pricePerLiterCents;

    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    @Column(name = "settled_at", insertable = false, updatable = false)
    private LocalDateTime settledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "chain_status", nullable = false)
    private ChainStatus chainStatus = ChainStatus.PENDING;

    @Column(name = "chain_tx_hash")
    private String chainTxHash;

    @Column(name = "chain_retried", nullable = false)
    private Integer chainRetried = 0;

    protected WaterBill() {
    }

    /** 由结算后的会话生成账单：液量/金额为本次结算的最终值。 */
    public static WaterBill of(WaterSession session, long totalLitersMilli, long totalAmountCents) {
        WaterBill b = new WaterBill();
        b.sessionId = session.getId();
        b.sn = session.getSn();
        b.userId = session.getUserId();
        b.litersMilli = totalLitersMilli;
        b.pricePerLiterCents = session.getPricePerLiterCents();
        b.amountCents = totalAmountCents;
        b.chainStatus = ChainStatus.PENDING;
        b.chainRetried = 0;
        return b;
    }

    public Long getId() { return id; }
    public Long getSessionId() { return sessionId; }
    public String getSn() { return sn; }
    public Long getUserId() { return userId; }
    public Long getLitersMilli() { return litersMilli; }
    public Integer getPricePerLiterCents() { return pricePerLiterCents; }
    public Long getAmountCents() { return amountCents; }
    public LocalDateTime getSettledAt() { return settledAt; }
    public ChainStatus getChainStatus() { return chainStatus; }
    public String getChainTxHash() { return chainTxHash; }
    public Integer getChainRetried() { return chainRetried; }
}
