package com.sanshuiyuan.h5.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 落地页主配置实体。hero/simulator/footer 以原始 JSON 字符串持有，由 Service 用 Jackson 解析为 DTO。
 * feature/trust badge 走独立子表，通过 config_id 关联（在 Service 内一次性按 sort 拉取，避免 N+1）。
 */
@Entity
@Table(name = "landing_config")
public class LandingConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ConfigStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "hero_json", nullable = false, columnDefinition = "json")
    private String heroJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "simulator_json", nullable = false, columnDefinition = "json")
    private String simulatorJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "footer_json", nullable = false, columnDefinition = "json")
    private String footerJson;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected LandingConfig() {
    }

    public Long getId() {
        return id;
    }

    public Integer getVersion() {
        return version;
    }

    public ConfigStatus getStatus() {
        return status;
    }

    public String getHeroJson() {
        return heroJson;
    }

    public String getSimulatorJson() {
        return simulatorJson;
    }

    public String getFooterJson() {
        return footerJson;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
