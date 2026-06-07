package com.sanshuiyuan.evidence.domain;

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
 * 用水账单（evidence-worker 侧只读/局部写映射，共享 water_db.water_bills 表）。
 * 仅映射存证所需字段；上链结果（chain_tx_hash / chain_status / chain_retried）由本服务推进。
 */
@Entity
@Table(name = "water_bills")
public class WaterBill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, insertable = false, updatable = false)
    private Long sessionId;

    @Column(name = "sn", nullable = false, insertable = false, updatable = false)
    private String sn;

    @Column(name = "user_id", nullable = false, insertable = false, updatable = false)
    private Long userId;

    @Column(name = "liters_milli", nullable = false, insertable = false, updatable = false)
    private Long litersMilli;

    @Column(name = "price_per_liter_cents", nullable = false, insertable = false, updatable = false)
    private Integer pricePerLiterCents;

    @Column(name = "amount_cents", nullable = false, insertable = false, updatable = false)
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
