package com.sanshuiyuan.asset.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "skus")
public class Sku {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false)
    private Long priceCents;

    @Column(nullable = false)
    private Long depositCents = 0L;

    @Column(columnDefinition = "TEXT")
    private String benefitsMd;

    @Column(length = 512)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SkuStatus status = SkuStatus.ACTIVE;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getPriceCents() { return priceCents; }
    public void setPriceCents(Long priceCents) { this.priceCents = priceCents; }
    public Long getDepositCents() { return depositCents; }
    public void setDepositCents(Long depositCents) { this.depositCents = depositCents; }
    public String getBenefitsMd() { return benefitsMd; }
    public void setBenefitsMd(String benefitsMd) { this.benefitsMd = benefitsMd; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public SkuStatus getStatus() { return status; }
    public void setStatus(SkuStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
