package com.sanshuiyuan.cend.checkout.domain;

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

    @Column(name = "phone_enc")
    private byte[] phoneEnc;

    @Column(name = "phone_mask", length = 16)
    private String phoneMask;

    @Column(name = "id_card_no_mask", nullable = false, length = 32)
    private String idCardNoMask;

    /** 身份证号确定性哈希（HMAC-SHA256），一证一号唯一性查询键。 */
    @Column(name = "id_card_hash", length = 64)
    private String idCardHash;

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
                                    String realNameMask, String idCardNoMask, String idCardHash,
                                    String certifyId, String channel,
                                    byte[] phoneEnc, String phoneMask) {
        KycRecord r = new KycRecord();
        r.openid = openid;
        r.realName = realName;
        r.idCardNoEnc = idCardNoEnc;
        r.realNameMask = realNameMask;
        r.idCardNoMask = idCardNoMask;
        r.idCardHash = idCardHash;
        r.certifyId = certifyId;
        r.channel = channel;
        r.phoneEnc = phoneEnc;
        r.phoneMask = phoneMask;
        r.status = KycStatus.PASS;
        r.verifiedAt = LocalDateTime.now();
        return r;
    }

    /** 发起认证时落 INIT 记录：绑定前端采集的实名信息到 certifyId，活体通过后再 promote。 */
    public static KycRecord createInit(String openid, byte[] realName, byte[] idCardNoEnc,
                                       String realNameMask, String idCardNoMask, String idCardHash,
                                       String certifyId, String channel,
                                       byte[] phoneEnc, String phoneMask) {
        KycRecord r = new KycRecord();
        r.openid = openid;
        r.realName = realName;
        r.idCardNoEnc = idCardNoEnc;
        r.realNameMask = realNameMask;
        r.idCardNoMask = idCardNoMask;
        r.idCardHash = idCardHash;
        r.certifyId = certifyId;
        r.channel = channel;
        r.phoneEnc = phoneEnc;
        r.phoneMask = phoneMask;
        r.status = KycStatus.INIT;
        return r;
    }

    /** 活体通过后置为 PASS。 */
    public void promoteToPass() {
        this.status = KycStatus.PASS;
        this.verifiedAt = LocalDateTime.now();
    }

    public void supersede() {
        this.status = KycStatus.SUPERSEDED;
    }

    /** 补充/更新手机号（用于早期已认证用户补录）。 */
    public void updatePhone(byte[] phoneEnc, String phoneMask) {
        this.phoneEnc = phoneEnc;
        this.phoneMask = phoneMask;
    }

    public Long getId() { return id; }
    public String getOpenid() { return openid; }
    public byte[] getRealName() { return realName; }
    public byte[] getIdCardNoEnc() { return idCardNoEnc; }
    public byte[] getPhoneEnc() { return phoneEnc; }
    public String getPhoneMask() { return phoneMask; }
    public String getIdCardNoMask() { return idCardNoMask; }
    public String getIdCardHash() { return idCardHash; }
    public String getRealNameMask() { return realNameMask; }
    public String getCertifyId() { return certifyId; }
    public String getChannel() { return channel; }
    public KycStatus getStatus() { return status; }
    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
