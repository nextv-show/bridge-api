package com.sanshuiyuan.user.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_roles")
public class UserRole {

    @EmbeddedId
    private UserRoleId id;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private LocalDateTime grantedAt;

    @PrePersist
    protected void onCreate() {
        grantedAt = LocalDateTime.now();
    }

    public UserRole() {}

    public UserRole(Long userId, Role role) {
        this.id = new UserRoleId(userId, role);
    }

    public UserRoleId getId() { return id; }
    public void setId(UserRoleId id) { this.id = id; }

    public LocalDateTime getGrantedAt() { return grantedAt; }

    @Embeddable
    public static class UserRoleId implements java.io.Serializable {
        @Column(name = "user_id", nullable = false)
        private Long userId;

        @Enumerated(EnumType.STRING)
        @Column(name = "role", nullable = false, length = 16)
        private Role role;

        public UserRoleId() {}

        public UserRoleId(Long userId, Role role) {
            this.userId = userId;
            this.role = role;
        }

        public Long getUserId() { return userId; }
        public Role getRole() { return role; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof UserRoleId that)) return false;
            return userId != null && userId.equals(that.userId) && role == that.role;
        }

        @Override
        public int hashCode() {
            return 31 * (userId != null ? userId.hashCode() : 0) + (role != null ? role.hashCode() : 0);
        }
    }
}
