package com.sanshuiyuan.cend.referral;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * H5 用户身份记录（cend-service 自有 {@code core_db.h5_users}，按微信 openid 唯一）。
 *
 * <p>背景：cend-service 以微信 openid 自有认证，canonical {@code users} 表归未部署的 user-service（见 008a T8a.1）。
 * 为使 H5 推广关系链（009/011）在「仅部署 cend-service」的真实生产环境可落地，cend-service 在 {@code core_db} 维护
 * 自有的轻量身份表：自增 {@code id} 即 ref_id 编码的「H5 user_id」，承载 L1/L2 关系链。
 *
 * <p><b>合规铁律（L1+L2 两级死锁）</b>：
 * <ul>
 *   <li>{@code inviterId}（L1）+ {@code grandInviterId}（L2）<b>仅在首次注册时一次性写入</b>，已注册用户不可改；</li>
 *   <li>{@code grandInviterId} 仅作单条快照存储/展示，<b>严禁以其为查询条件向上递归追溯</b>（L3+ 物理隔离，见
 *       {@link CendUserRepository}）。</li>
 * </ul>
 */
@Entity
@Table(name = "h5_users")
public class CendUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String openid;

    /** 微信昵称快照（014），登录时写入。仅用于邀请确认页脱敏展示，与关系链无关。 */
    @Column(length = 64)
    private String nickname;

    /** 微信头像 URL 快照（014），登录时写入。仅用于邀请确认页展示，与关系链无关。 */
    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    /** L1 邀请人 H5 user_id（自然流量为 null）。仅首次注册写入，已注册用户不可改。 */
    @Column(name = "inviter_id")
    private Long inviterId;

    /** L2 间接邀请人 H5 user_id（可 null）。仅作单条快照展示，严禁以此为条件向上递归（L3+ 物理隔离）。 */
    @Column(name = "grand_inviter_id")
    private Long grandInviterId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected CendUser() {
    }

    /** 创建一个尚未绑定关系链的自然流量 H5 用户。 */
    public static CendUser create(String openid) {
        CendUser u = new CendUser();
        u.openid = openid;
        return u;
    }

    /**
     * 一次性写入 L1/L2 关系链。仅供首次注册调用；调用方须保证已做自我邀请/解码失败降级判断。
     * 不做任何向上递归追溯，{@code grandInviterId} 由调用方按「邀请人的 inviter_id」一次性快照得到。
     */
    public void bindReferral(Long inviterId, Long grandInviterId) {
        this.inviterId = inviterId;
        this.grandInviterId = grandInviterId;
    }

    /**
     * 刷新微信资料快照（昵称/头像）。仅在传入值非空且确有变化时更新，
     * 避免登录未携带资料时把既有快照清空。
     *
     * @return 是否发生变更（用于调用方决定是否落库）。
     */
    public boolean updateProfile(String nickname, String avatarUrl) {
        boolean changed = false;
        if (nickname != null && !nickname.isBlank() && !nickname.equals(this.nickname)) {
            this.nickname = nickname;
            changed = true;
        }
        if (avatarUrl != null && !avatarUrl.isBlank() && !avatarUrl.equals(this.avatarUrl)) {
            this.avatarUrl = avatarUrl;
            changed = true;
        }
        return changed;
    }

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
    public String getOpenid() { return openid; }
    public String getNickname() { return nickname; }
    public String getAvatarUrl() { return avatarUrl; }
    public Long getInviterId() { return inviterId; }
    public Long getGrandInviterId() { return grandInviterId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
