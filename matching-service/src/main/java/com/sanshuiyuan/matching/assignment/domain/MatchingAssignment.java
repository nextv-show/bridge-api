package com.sanshuiyuan.matching.assignment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.LocalDateTime;

/** 接单绑定记录（matching_assignments，落 core_db）。 */
@Entity
@Table(name = "matching_assignments")
public class MatchingAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "device_asset_id", nullable = false)
    private Long deviceAssetId;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Generated(event = EventType.INSERT)   // DB 默认 CURRENT_TIMESTAMP 写入后回读入实体
    @Column(name = "locked_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime lockedAt;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }

    public Long getDeviceAssetId() { return deviceAssetId; }
    public void setDeviceAssetId(Long deviceAssetId) { this.deviceAssetId = deviceAssetId; }

    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }

    public LocalDateTime getLockedAt() { return lockedAt; }
    public LocalDateTime getReleasedAt() { return releasedAt; }
    public void setReleasedAt(LocalDateTime releasedAt) { this.releasedAt = releasedAt; }
}
