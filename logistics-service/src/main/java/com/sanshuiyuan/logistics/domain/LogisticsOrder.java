package com.sanshuiyuan.logistics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.LocalDateTime;

/** 物流工单（logistics_orders，落 core_db）。V010 建表。 */
@Entity
@Table(name = "logistics_orders")
public class LogisticsOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "device_asset_id", nullable = false)
    private Long deviceAssetId;

    @Column(name = "ship_to_address_snapshot", nullable = false, columnDefinition = "json")
    private String shipToAddressSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private LogisticsStatus status;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }

    public Long getDeviceAssetId() { return deviceAssetId; }
    public void setDeviceAssetId(Long deviceAssetId) { this.deviceAssetId = deviceAssetId; }

    public String getShipToAddressSnapshot() { return shipToAddressSnapshot; }
    public void setShipToAddressSnapshot(String shipToAddressSnapshot) { this.shipToAddressSnapshot = shipToAddressSnapshot; }

    public LogisticsStatus getStatus() { return status; }
    public void setStatus(LogisticsStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
