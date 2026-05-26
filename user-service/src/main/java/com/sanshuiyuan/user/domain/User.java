package com.sanshuiyuan.user.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 64)
    private String unionid;

    @Column(name = "openid_wx", length = 64)
    private String openidWx;

    @Column(name = "openid_app", length = 64)
    private String openidApp;

    @Column(length = 64)
    private String nickname;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "active_role", nullable = false)
    private Role activeRole = Role.CONSUMER;

    /** L1 邀请人 user_id（自然流量为 null）。仅首次注册写入，已注册用户不可改（绑定逻辑见 008b）。 */
    @Column(name = "inviter_id")
    private Long inviterId;

    /** L2 间接邀请人 user_id（可 null）。仅作单条快照展示，严禁以此为条件向上递归（L3+ 物理隔离）。 */
    @Column(name = "grand_inviter_id")
    private Long grandInviterId;

    /** 多端账号统一识别键（手机号，可 null）。 */
    @Column(length = 20)
    private String phone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUnionid() { return unionid; }
    public void setUnionid(String unionid) { this.unionid = unionid; }

    public String getOpenidWx() { return openidWx; }
    public void setOpenidWx(String openidWx) { this.openidWx = openidWx; }

    public String getOpenidApp() { return openidApp; }
    public void setOpenidApp(String openidApp) { this.openidApp = openidApp; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public Role getActiveRole() { return activeRole; }
    public void setActiveRole(Role activeRole) { this.activeRole = activeRole; }

    public Long getInviterId() { return inviterId; }
    public void setInviterId(Long inviterId) { this.inviterId = inviterId; }

    public Long getGrandInviterId() { return grandInviterId; }
    public void setGrandInviterId(Long grandInviterId) { this.grandInviterId = grandInviterId; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
