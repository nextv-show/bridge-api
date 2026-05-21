package com.sanshuiyuan.admin.domain;

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

    @Column(name = "price_cents", nullable = false)
    private Long priceCents;

    @Column(name = "deposit_cents", nullable = false)
    private Long depositCents = 0L;

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

    public enum Status { ACTIVE, INACTIVE }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Long getPriceCents() { return priceCents; }
    public Long getDepositCents() { return depositCents; }
    public String getBenefitsMd() { return benefitsMd; }
    public String getImageUrl() { return imageUrl; }
    public Status getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setName(String name) { this.name = name; }
    public void setPriceCents(Long priceCents) { this.priceCents = priceCents; }
    public void setDepositCents(Long depositCents) { this.depositCents = depositCents; }
    public void setBenefitsMd(String benefitsMd) { this.benefitsMd = benefitsMd; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setStatus(Status status) { this.status = status; }
}
