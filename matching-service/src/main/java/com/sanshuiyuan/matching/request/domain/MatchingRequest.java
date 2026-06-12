package com.sanshuiyuan.matching.request.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 撮合需求单（matching_requests，落 core_db）。列与 V010 完全对齐（ddl-auto=validate）。 */
@Entity
@Table(name = "matching_requests")
public class MatchingRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "contact_name", nullable = false, length = 64)
    private String contactName;

    @Column(name = "contact_phone_enc", nullable = false)
    private byte[] contactPhoneEnc;

    @Column(name = "contact_phone_hash", nullable = false, length = 64)
    private String contactPhoneHash;

    @Column(name = "address", nullable = false, length = 255)
    private String address;

    @Column(name = "lat", nullable = false, precision = 10, scale = 7)
    private BigDecimal lat;

    @Column(name = "lng", nullable = false, precision = 10, scale = 7)
    private BigDecimal lng;

    @Column(name = "geohash6", nullable = false, length = 6)
    private String geohash6;

    @Enumerated(EnumType.STRING)
    @Column(name = "scene_type", nullable = false)
    private SceneType sceneType;

    @Column(name = "est_daily_liters", nullable = false)
    private Integer estDailyLiters;

    @Enumerated(EnumType.STRING)
    @Column(name = "expected_price_tier", nullable = false)
    private PriceTier expectedPriceTier;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RequestStatus status;

    @Column(name = "locked_by_user_id")
    private Long lockedByUserId;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    /** P1-2 软标记：接单后确认推进时间；NULL=待确认（24h 内未确认由 SLA 任务自动释放）。 */
    @Column(name = "claim_confirmed_at")
    private LocalDateTime claimConfirmedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }

    public byte[] getContactPhoneEnc() { return contactPhoneEnc; }
    public void setContactPhoneEnc(byte[] contactPhoneEnc) { this.contactPhoneEnc = contactPhoneEnc; }

    public String getContactPhoneHash() { return contactPhoneHash; }
    public void setContactPhoneHash(String contactPhoneHash) { this.contactPhoneHash = contactPhoneHash; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public BigDecimal getLat() { return lat; }
    public void setLat(BigDecimal lat) { this.lat = lat; }

    public BigDecimal getLng() { return lng; }
    public void setLng(BigDecimal lng) { this.lng = lng; }

    public String getGeohash6() { return geohash6; }
    public void setGeohash6(String geohash6) { this.geohash6 = geohash6; }

    public SceneType getSceneType() { return sceneType; }
    public void setSceneType(SceneType sceneType) { this.sceneType = sceneType; }

    public Integer getEstDailyLiters() { return estDailyLiters; }
    public void setEstDailyLiters(Integer estDailyLiters) { this.estDailyLiters = estDailyLiters; }

    public PriceTier getExpectedPriceTier() { return expectedPriceTier; }
    public void setExpectedPriceTier(PriceTier expectedPriceTier) { this.expectedPriceTier = expectedPriceTier; }

    public RequestStatus getStatus() { return status; }
    public void setStatus(RequestStatus status) { this.status = status; }

    public Long getLockedByUserId() { return lockedByUserId; }
    public void setLockedByUserId(Long lockedByUserId) { this.lockedByUserId = lockedByUserId; }

    public LocalDateTime getLockedAt() { return lockedAt; }
    public void setLockedAt(LocalDateTime lockedAt) { this.lockedAt = lockedAt; }

    public LocalDateTime getClaimConfirmedAt() { return claimConfirmedAt; }
    public void setClaimConfirmedAt(LocalDateTime claimConfirmedAt) { this.claimConfirmedAt = claimConfirmedAt; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
