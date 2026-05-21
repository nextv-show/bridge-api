package com.sanshuiyuan.admin.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "kyc_records")
public class KycRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "real_name", nullable = false, length = 128)
    private String realName;

    @Column(name = "id_number", nullable = false, length = 128)
    private String idNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public enum Status { PENDING, PASS, REJECT }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getRealName() { return realName; }
    public String getIdNumber() { return idNumber; }
    public Status getStatus() { return status; }
    public Long getReviewedBy() { return reviewedBy; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void approve(Long reviewerId) {
        this.status = Status.PASS;
        this.reviewedBy = reviewerId;
        this.reviewedAt = LocalDateTime.now();
    }

    public void reject(Long reviewerId) {
        this.status = Status.REJECT;
        this.reviewedBy = reviewerId;
        this.reviewedAt = LocalDateTime.now();
    }
}
