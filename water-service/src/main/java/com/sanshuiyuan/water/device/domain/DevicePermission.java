package com.sanshuiyuan.water.device.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 设备出水权限（每 SN 一行）。{@code canDispense=false} 时 {@code lockedReason} 给出原因；
 * 由 002 上游 outbox 投影维护（见 DevicePermissionProjector）。
 */
@Entity
@Table(name = "device_permissions")
public class DevicePermission {

    @Id
    private String sn;

    @Column(name = "can_dispense", nullable = false)
    private boolean canDispense;

    @Column(name = "locked_reason")
    @Enumerated(EnumType.STRING)
    private LockedReason lockedReason;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected DevicePermission() {
    }

    public DevicePermission(String sn) {
        this.sn = sn;
    }

    public String getSn() { return sn; }
    public boolean isCanDispense() { return canDispense; }
    public LockedReason getLockedReason() { return lockedReason; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setCanDispense(boolean canDispense) { this.canDispense = canDispense; }
    public void setLockedReason(LockedReason lockedReason) { this.lockedReason = lockedReason; }
}
