package com.sanshuiyuan.admin.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Admin 只读映射 h5-service 创建的 kyc_records 表。
 * 真实数据（加密身份证等）由 h5-service 写入；
 * admin-service 仅做审核状态变更。
 */
@Entity
@Table(name = "kyc_records")
public class KycRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "openid", nullable = false, length = 64)
    private String openid;

    /** AES 加密真实姓名（h5-service IdCardCipher 写入） */
    @Column(name = "real_name")
    private byte[] realNameEncrypted;

    /** AES 加密身份证号（h5-service IdCardCipher 写入） */
    @Column(name = "id_card_no_enc", nullable = false)
    private byte[] idCardNoEncrypted;

    /** 脱敏身份证号（展示用） */
    @Column(name = "id_card_no_mask", nullable = false, length = 32)
    private String idCardNoMask;

    /** 脱敏姓名（展示用） */
    @Column(name = "real_name_mask", length = 32)
    private String realNameMask;

    /** 阿里云认证流水号 */
    @Column(name = "certify_id", length = 64)
    private String certifyId;

    @Column(name = "channel", nullable = false, length = 32)
    private String channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 人工驳回原因（admin 扩展，仅 REJECT 时写入） */
    @Column(name = "reject_reason", length = 200)
    private String rejectReason;

    /**
     * admin 审核状态扩展 — 映射 h5-service 的 enum + 扩展。
     * h5-service: FAIL, INIT, PASS, SUPERSEDED
     * admin 扩展: 增加 PENDING/REJECT 用于人工审核
     */
    public enum Status {
        FAIL,       // 阿里云认证失败
        INIT,       // 已发起未完成
        PENDING,    // 待人工审核（admin 扩展）
        PASS,       // 通过
        REJECT,     // 人工驳回（admin 扩展）
        SUPERSEDED  // 被新记录取代
    }

    public Long getId() { return id; }
    public String getOpenid() { return openid; }
    public byte[] getRealNameEncrypted() { return realNameEncrypted; }
    public byte[] getIdCardNoEncrypted() { return idCardNoEncrypted; }
    public String getIdCardNoMask() { return idCardNoMask; }
    public String getRealNameMask() { return realNameMask; }
    public String getCertifyId() { return certifyId; }
    public String getChannel() { return channel; }
    public Status getStatus() { return status; }
    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getRejectReason() { return rejectReason; }

    /** 人工审核通过 — 仅 admin 调用 */
    public void approve() {
        this.status = Status.PASS;
        this.verifiedAt = LocalDateTime.now();
    }

    /** 人工审核驳回 — 仅 admin 调用 */
    public void reject(String reason) {
        this.status = Status.REJECT;
        this.verifiedAt = LocalDateTime.now();
        this.rejectReason = reason;
    }
}
