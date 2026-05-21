package com.sanshuiyuan.h5.checkout.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "kyc_records")
public class KycRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String openid;

    @Column(name = "real_name")
    private byte[] realName;

    @Column(name = "id_card_no_enc", nullable = false)
    private byte[] idCardNoEnc;

    @Column(name = "id_card_no_mask", nullable = false, length = 32)
    private String idCardNoMask;

    @Column(name = "real_name_mask", nullable = false, length = 32)
    private String realNameMask;

    @Column(name = "certify_id", length = 64)
    private String certifyId;

    @Column(nullable = false, length = 32)
    private String channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private KycStatus status;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected KycRecord() {
    }

    public static KycRecord create(String openid, byte[] realName, byte[] idCardNoEnc,
                                    String realNameMask, String idCardNoMask,
                                    String certifyId, String channel) {
        KycRecord r = new KycRecord();
        r.openid = openid;
        r.realName = realName;
        r.idCardNoEnc = idCardNoEnc;
        r.realNameMask = realNameMask;
        r.idCardNoMask = idCardNoMask;
        r.certifyId = certifyId;
        r.channel = channel;
        r.status = KycStatus.PASS;
        r.verifiedAt = LocalDateTime.now();
        return r;
    }

    public void supersede() {
        this.status = KycStatus.SUPERSEDED;
    }

    public Long getId() { return id; }
    public String getOpenid() { return openid; }
    public byte[] getRealName() { return realName; }
    public byte[] getIdCardNoEnc() { return idCardNoEnc; }
    public String getIdCardNoMask() { return idCardNoMask; }
    public String getRealNameMask() { return realNameMask; }
    public String getCertifyId() { return certifyId; }
    public String getChannel() { return channel; }
    public KycStatus getStatus() { return status; }
    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
