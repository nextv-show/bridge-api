package com.sanshuiyuan.settlement.infra.user;

import jakarta.persistence.*;

/** core_db.users 只读实体：提现/合规校验需要的 KYC 状态。 */
@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kyc_status", nullable = false, length = 16)
    private String kycStatus;

    @Column(name = "kyc_verified_at")
    private java.time.LocalDateTime kycVerifiedAt;

    protected UserEntity() {}

    public Long getId() { return id; }
    public String getKycStatus() { return kycStatus; }
    public java.time.LocalDateTime getKycVerifiedAt() { return kycVerifiedAt; }
}
