package com.sanshuiyuan.cend.identity;

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
 * 跨端身份关联（core_db.identity_links）：某端 openid → 同一自然人 id_card_hash 的"仅可见"绑定。
 *
 * <p>由"微信手机号核验"建立：用户在小程序一键拿到微信级已验证手机号，匹配到其在另一端（公众号）已实名
 * (PASS) 记录的 id_card_hash，从而把本端 openid 关联到该自然人。{@link IdentityResolver} 据此按自然人
 * 聚合订单（读路径）。
 *
 * <p><b>边界</b>：本关联<b>仅</b>解锁历史订单可见，<b>绝不</b>等同于 KYC PASS——CreateOrderUseCase 的实名闸口
 * 只认 kyc_records 的 PASS，phone 关联不授予认购/实名资格。
 */
@Entity
@Table(name = "identity_links")
public class IdentityLink {

    public enum Source { PHONE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String openid;

    @Column(name = "id_card_hash", nullable = false, length = 64)
    private String idCardHash;

    @Column(nullable = false, length = 16)
    private String source = Source.PHONE.name();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected IdentityLink() {
    }

    public static IdentityLink phone(String openid, String idCardHash) {
        IdentityLink link = new IdentityLink();
        link.openid = openid;
        link.idCardHash = idCardHash;
        link.source = Source.PHONE.name();
        return link;
    }

    /** 同一 openid 重复核验：刷新关联到的自然人（如换号/数据修正）。 */
    public void relink(String idCardHash) {
        this.idCardHash = idCardHash;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getOpenid() { return openid; }
    public String getIdCardHash() { return idCardHash; }
    public String getSource() { return source; }
}
