package com.sanshuiyuan.h5.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 区块01「核心能力」FeatureCard 子项（4 张）。
 */
@Entity
@Table(name = "landing_feature")
public class LandingFeature {

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

    @Column(length = 255)
    private String descr;

    @Column(name = "icon_key", nullable = false, length = 32)
    private String iconKey;

    protected LandingFeature() {
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

    public String getDescr() {
        return descr;
    }

    public String getIconKey() {
        return iconKey;
    }
}
