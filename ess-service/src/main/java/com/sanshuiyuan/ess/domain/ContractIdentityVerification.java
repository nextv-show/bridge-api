package com.sanshuiyuan.ess.domain;

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
 * 合同签署前身份核验记录实体。
 * <p>
 * 记录每次身份核验的完整生命周期：创建 → 腾讯电子签核验 → 结果回调。
 */
@Entity
@Table(name = "contract_identity_verifications")
public class ContractIdentityVerification {

    /**
     * 核验状态枚举。
     */
    public enum Status {
        /** 待核验 */
        PENDING,
        /** 核验中（已发起人脸识别） */
        IN_PROGRESS,
        /** 核验通过 */
        PASSED,
        /** 核验失败 */
        FAILED
    }

    /**
     * 核验类型枚举。
     */
    public enum VerificationType {
        /** 人脸识别 */
        FACE,
        /** 身份证比对 */
        ID_CARD
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_id", nullable = false, length = 64)
    private String contractId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "kyc_record_id", length = 64)
    private String kycRecordId;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_type", nullable = false, length = 32)
    private VerificationType verificationType;

    @Column(name = "ess_verification_id", length = 128)
    private String essVerificationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private Status status;

    @Column(name = "face_score", precision = 5, scale = 2)
    private java.math.BigDecimal faceScore;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "failure_reason", length = 512)
    private String failureReason;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ContractIdentityVerification() {
    }

    /**
     * 创建新的身份核验记录。
     */
    public static ContractIdentityVerification create(String contractId, Long userId,
                                                       String kycRecordId,
                                                       VerificationType verificationType) {
        ContractIdentityVerification v = new ContractIdentityVerification();
        v.contractId = contractId;
        v.userId = userId;
        v.kycRecordId = kycRecordId;
        v.verificationType = verificationType;
        v.status = Status.PENDING;
        v.retryCount = 0;
        return v;
    }

    /**
     * 发起核验（状态变为 IN_PROGRESS）。
     */
    public void startVerification(String essVerificationId) {
        this.essVerificationId = essVerificationId;
        this.status = Status.IN_PROGRESS;
    }

    /**
     * 核验通过。
     */
    public void pass(java.math.BigDecimal faceScore) {
        this.status = Status.PASSED;
        this.faceScore = faceScore;
        this.verifiedAt = LocalDateTime.now();
        this.failureReason = null;
    }

    /**
     * 核验失败。
     */
    public void fail(String failureReason) {
        this.status = Status.FAILED;
        this.failureReason = failureReason;
        this.retryCount++;
    }

    /**
     * 判断是否可以重试。
     *
     * @param maxRetriesPerDay 每日最大重试次数
     * @return 是否可以重试
     */
    public boolean canRetry(int maxRetriesPerDay) {
        return this.retryCount < maxRetriesPerDay;
    }

    /**
     * 重置为待核验状态（用于重试）。
     */
    public void resetForRetry() {
        this.status = Status.PENDING;
        this.essVerificationId = null;
        this.faceScore = null;
        this.failureReason = null;
    }

    public Long getId() { return id; }
    public String getContractId() { return contractId; }
    public Long getUserId() { return userId; }
    public String getKycRecordId() { return kycRecordId; }
    public VerificationType getVerificationType() { return verificationType; }
    public String getEssVerificationId() { return essVerificationId; }
    public Status getStatus() { return status; }
    public java.math.BigDecimal getFaceScore() { return faceScore; }
    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public String getFailureReason() { return failureReason; }
    public int getRetryCount() { return retryCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
