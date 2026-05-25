package com.sanshuiyuan.admin.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * C 端用户实体 — 对应 asset_db.users 表（admin-service 拥有的去规范化表）。
 * status/channel/tier/kycStatus 以 VARCHAR 存储（与迁移一致），用普通 String 字段避免枚举不匹配。
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 64)
    private String openid;

    @Column(length = 64)
    private String nickname;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    @Column(length = 8)
    private String gender;

    @Column
    private Integer age;

    @Column(name = "phone_mask", length = 32)
    private String phoneMask;

    @Column(name = "real_name_mask", length = 32)
    private String realNameMask;

    @Column(nullable = false, length = 16)
    private String channel = "WECHAT_MP";

    @Column(nullable = false, length = 16)
    private String tier = "NORMAL";

    @Column(nullable = false, length = 256)
    private String tags = "";

    @Column(length = 64)
    private String city;

    @Column(nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(name = "frozen_reason", length = 256)
    private String frozenReason;

    @Column(name = "frozen_duration", length = 16)
    private String frozenDuration;

    @Column(name = "frozen_at")
    private LocalDateTime frozenAt;

    @Column(name = "frozen_by", length = 64)
    private String frozenBy;

    @Column(name = "kyc_status", nullable = false, length = 16)
    private String kycStatus = "NONE";

    @Column(name = "kyc_verified_at")
    private LocalDateTime kycVerifiedAt;

    @Column(length = 512)
    private String note;

    @Column(name = "last_active_at")
    private LocalDateTime lastActiveAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 标签：逗号分隔字符串 → List（空字符串返回 []）。 */
    public List<String> tagList() {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /* ========== getter / setter ========== */

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOpenid() { return openid; }
    public void setOpenid(String openid) { this.openid = openid; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }

    public String getPhoneMask() { return phoneMask; }
    public void setPhoneMask(String phoneMask) { this.phoneMask = phoneMask; }

    public String getRealNameMask() { return realNameMask; }
    public void setRealNameMask(String realNameMask) { this.realNameMask = realNameMask; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getFrozenReason() { return frozenReason; }
    public void setFrozenReason(String frozenReason) { this.frozenReason = frozenReason; }

    public String getFrozenDuration() { return frozenDuration; }
    public void setFrozenDuration(String frozenDuration) { this.frozenDuration = frozenDuration; }

    public LocalDateTime getFrozenAt() { return frozenAt; }
    public void setFrozenAt(LocalDateTime frozenAt) { this.frozenAt = frozenAt; }

    public String getFrozenBy() { return frozenBy; }
    public void setFrozenBy(String frozenBy) { this.frozenBy = frozenBy; }

    public String getKycStatus() { return kycStatus; }
    public void setKycStatus(String kycStatus) { this.kycStatus = kycStatus; }

    public LocalDateTime getKycVerifiedAt() { return kycVerifiedAt; }
    public void setKycVerifiedAt(LocalDateTime kycVerifiedAt) { this.kycVerifiedAt = kycVerifiedAt; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LocalDateTime getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(LocalDateTime lastActiveAt) { this.lastActiveAt = lastActiveAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
