package com.sanshuiyuan.h5.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 区块03「购置保障」TrustBadge 子项（4–6 个）。
 */
@Entity
@Table(name = "landing_trust_badge")
public class LandingTrustBadge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_id", nullable = false)
    private Long configId;

    @Column(nullable = false)
    private Integer sort;

    @Column(nullable = false, length = 64)
    private String title;

    @Column(length = 64)
    private String subtitle;

    @Column(name = "icon_key", nullable = false, length = 32)
    private String iconKey;

    protected LandingTrustBadge() {
    }

    public Long getId() {
        return id;
    }

    public Long getConfigId() {
        return configId;
    }

    public Integer getSort() {
        return sort;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getIconKey() {
        return iconKey;
    }
}
