package com.sanshuiyuan.admin.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "skus")
public class Sku {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 32)
    private String code;

    @Column(length = 255)
    private String subtitle;

    @Column(length = 32, nullable = false)
    private String category = "HOME";

    @Column(name = "price_cents", nullable = false)
    private Long priceCents;

    @Column(name = "original_cents", nullable = false)
    private Long originalCents = 0L;

    @Column(name = "cost_cents", nullable = false)
    private Long costCents = 0L;

    @Column(name = "deposit_cents", nullable = false)
    private Long depositCents = 0L;

    @Column(nullable = false)
    private Integer stock = 0;

    @Column(name = "stock_warn", nullable = false)
    private Integer stockWarn = 0;

    @Column(name = "sold_30d", nullable = false)
    private Integer sold30d = 0;

    @Column(name = "s1_months", nullable = false)
    private Integer s1Months = 0;

    @Column(name = "s2_months", nullable = false)
    private Integer s2Months = 0;

    @Column(name = "fuse_at", nullable = false)
    private Integer fuseAt = 0;

    @Column(name = "annualized_bp", nullable = false)
    private Integer annualizedBp = 0;

    @Column(name = "sold_total", nullable = false)
    private Integer soldTotal = 0;

    @Column(name = "refund_rate", precision = 6, scale = 4, nullable = false)
    private BigDecimal refundRate = BigDecimal.ZERO;

    @Column(name = "gmv_cents", nullable = false)
    private Long gmvCents = 0L;

    @Column(precision = 6, scale = 4, nullable = false)
    private BigDecimal conversion = BigDecimal.ZERO;

    @Column(length = 16, nullable = false)
    private String accent = "#5BA8FF";

    @Column(nullable = false)
    private Boolean featured = false;

    @Column(name = "low_stock", nullable = false)
    private Boolean lowStock = false;

    @Column(name = "no_stage", nullable = false)
    private Boolean noStage = false;

    @Column(name = "draft_col", nullable = false)
    private Boolean draft = false;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "benefits_md", columnDefinition = "TEXT")
    private String benefitsMd;

    @Column(name = "image_url", length = 512)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum Status { ACTIVE, PAUSED, OFFLINE, DRAFT, INACTIVE }

    // Getters
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCode() { return code; }
    public String getSubtitle() { return subtitle; }
    public String getCategory() { return category; }
    public Long getPriceCents() { return priceCents; }
    public Long getOriginalCents() { return originalCents; }
    public Long getCostCents() { return costCents; }
    public Long getDepositCents() { return depositCents; }
    public Integer getStock() { return stock; }
    public Integer getStockWarn() { return stockWarn; }
    public Integer getSold30d() { return sold30d; }
    public Integer getS1Months() { return s1Months; }
    public Integer getS2Months() { return s2Months; }
    public Integer getFuseAt() { return fuseAt; }
    public Integer getAnnualizedBp() { return annualizedBp; }
    public Integer getSoldTotal() { return soldTotal; }
    public BigDecimal getRefundRate() { return refundRate; }
    public Long getGmvCents() { return gmvCents; }
    public BigDecimal getConversion() { return conversion; }
    public String getAccent() { return accent; }
    public Boolean getFeatured() { return featured; }
    public Boolean getLowStock() { return lowStock; }
    public Boolean getNoStage() { return noStage; }
    public Boolean getDraft() { return draft; }
    public String getNote() { return note; }
    public String getBenefitsMd() { return benefitsMd; }
    public String getImageUrl() { return imageUrl; }
    public Status getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    // Setters
    public void setName(String name) { this.name = name; }
    public void setCode(String code) { this.code = code; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
    public void setCategory(String category) { this.category = category; }
    public void setPriceCents(Long priceCents) { this.priceCents = priceCents; }
    public void setOriginalCents(Long originalCents) { this.originalCents = originalCents; }
    public void setCostCents(Long costCents) { this.costCents = costCents; }
    public void setDepositCents(Long depositCents) { this.depositCents = depositCents; }
    public void setStock(Integer stock) { this.stock = stock; }
    public void setStockWarn(Integer stockWarn) { this.stockWarn = stockWarn; }
    public void setSold30d(Integer sold30d) { this.sold30d = sold30d; }
    public void setS1Months(Integer s1Months) { this.s1Months = s1Months; }
    public void setS2Months(Integer s2Months) { this.s2Months = s2Months; }
    public void setFuseAt(Integer fuseAt) { this.fuseAt = fuseAt; }
    public void setAnnualizedBp(Integer annualizedBp) { this.annualizedBp = annualizedBp; }
    public void setSoldTotal(Integer soldTotal) { this.soldTotal = soldTotal; }
    public void setRefundRate(BigDecimal refundRate) { this.refundRate = refundRate; }
    public void setGmvCents(Long gmvCents) { this.gmvCents = gmvCents; }
    public void setConversion(BigDecimal conversion) { this.conversion = conversion; }
    public void setAccent(String accent) { this.accent = accent; }
    public void setFeatured(Boolean featured) { this.featured = featured; }
    public void setLowStock(Boolean lowStock) { this.lowStock = lowStock; }
    public void setNoStage(Boolean noStage) { this.noStage = noStage; }
    public void setDraft(Boolean draft) { this.draft = draft; }
    public void setNote(String note) { this.note = note; }
    public void setBenefitsMd(String benefitsMd) { this.benefitsMd = benefitsMd; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setStatus(Status status) { this.status = status; }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
