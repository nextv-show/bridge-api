package com.sanshuiyuan.iot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 设备在线状态（每 SN 一行，UPSERT 维护）。{@code lastLwtAt} 记录最近一次 LWT（遗嘱）离线时间。
 */
@Entity
@Table(name = "device_status")
public class DeviceStatus {

    @Id
    private String sn;

    private boolean online;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Column(name = "last_lwt_at")
    private LocalDateTime lastLwtAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected DeviceStatus() {
    }

    public DeviceStatus(String sn) {
        this.sn = sn;
    }

    public String getSn() { return sn; }
    public boolean isOnline() { return online; }
    public LocalDateTime getLastSeenAt() { return lastSeenAt; }
    public LocalDateTime getLastLwtAt() { return lastLwtAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setOnline(boolean online) { this.online = online; }
    public void setLastSeenAt(LocalDateTime lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public void setLastLwtAt(LocalDateTime lastLwtAt) { this.lastLwtAt = lastLwtAt; }
}
