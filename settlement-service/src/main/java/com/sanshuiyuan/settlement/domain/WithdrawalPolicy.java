package com.sanshuiyuan.settlement.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** 提现策略：手续费率（基点）、单笔与单日上限，按生效时间取最新一条。 */
@Entity
@Table(name = "withdrawal_policies")
public class WithdrawalPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fee_bp", nullable = false)
    private Integer feeBp;

    @Column(name = "single_max_cents", nullable = false)
    private Long singleMaxCents;

    @Column(name = "daily_max_cents", nullable = false)
    private Long dailyMaxCents;

    @Column(name = "daily_max_count", nullable = false)
    private Integer dailyMaxCount;

    @Column(name = "min_cents", nullable = false)
    private Long minCents;

    @Column(name = "effective_from", nullable = false)
    private LocalDateTime effectiveFrom;

    protected WithdrawalPolicy() {}

    public WithdrawalPolicy(Integer feeBp, Long singleMaxCents, Long dailyMaxCents,
                            Integer dailyMaxCount, Long minCents, LocalDateTime effectiveFrom) {
        this.feeBp = feeBp;
        this.singleMaxCents = singleMaxCents;
        this.dailyMaxCents = dailyMaxCents;
        this.dailyMaxCount = dailyMaxCount;
        this.minCents = minCents;
        this.effectiveFrom = effectiveFrom;
    }

    public Long getId() { return id; }
    public Integer getFeeBp() { return feeBp; }
    public void setFeeBp(Integer feeBp) { this.feeBp = feeBp; }
    public Long getSingleMaxCents() { return singleMaxCents; }
    public void setSingleMaxCents(Long singleMaxCents) { this.singleMaxCents = singleMaxCents; }
    public Long getDailyMaxCents() { return dailyMaxCents; }
    public void setDailyMaxCents(Long dailyMaxCents) { this.dailyMaxCents = dailyMaxCents; }
    public Integer getDailyMaxCount() { return dailyMaxCount; }
    public void setDailyMaxCount(Integer dailyMaxCount) { this.dailyMaxCount = dailyMaxCount; }
    public Long getMinCents() { return minCents; }
    public void setMinCents(Long minCents) { this.minCents = minCents; }
    public LocalDateTime getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDateTime effectiveFrom) { this.effectiveFrom = effectiveFrom; }
}
