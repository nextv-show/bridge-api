package com.sanshuiyuan.user.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "home_layout_pref")
public class HomeLayoutPref {

    @Id
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "active_role", nullable = false)
    private Role activeRole = Role.CONSUMER;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Role getActiveRole() { return activeRole; }
    public void setActiveRole(Role activeRole) { this.activeRole = activeRole; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
