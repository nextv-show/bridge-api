package com.sanshuiyuan.settlement.infra.water;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * water_db.water_bills 的只读实体（跨库查询）。
 * catalog="water_db" → Hibernate 生成 SELECT ... FROM water_db.water_bills（同实例跨库）。
 */
@Entity
@Table(name = "water_bills", catalog = "water_db")
public class WaterBillEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "session_id", nullable = false)
    private Long sessionId;
    @Column(nullable = false)
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

    protected WaterBillEntity() {}
    public Long getId() { return id; }
    public Long getSessionId() { return sessionId; }
    public String getSn() { return sn; }
    public Long getUserId() { return userId; }
    public Long getLitersMilli() { return litersMilli; }
    public Integer getPricePerLiterCents() { return pricePerLiterCents; }
    public Long getAmountCents() { return amountCents; }
    public LocalDateTime getSettledAt() { return settledAt; }
}
