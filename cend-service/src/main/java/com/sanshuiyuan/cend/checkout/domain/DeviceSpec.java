package com.sanshuiyuan.cend.checkout.domain;

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

@Entity
@Table(name = "device_specs")
public class DeviceSpec {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "spec_id", nullable = false, unique = true, length = 32)
    private String specId;

    @Column(name = "model_code", nullable = false, length = 32)
    private String modelCode;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "price_cents", nullable = false)
    private Long priceCents;

    @Column(nullable = false)
    private boolean recommended;

    @Column(name = "monitor_line", length = 128)
    private String monitorLine;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "features_json", nullable = false, columnDefinition = "json")
    private String featuresJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SpecStatus status;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected DeviceSpec() {
    }

    public enum SpecStatus { ACTIVE, INACTIVE }

    public Long getId() { return id; }
    public String getSpecId() { return specId; }
    public String getModelCode() { return modelCode; }
    public String getName() { return name; }
    public Long getPriceCents() { return priceCents; }
    public boolean isRecommended() { return recommended; }
    public String getMonitorLine() { return monitorLine; }
    public String getFeaturesJson() { return featuresJson; }
    public SpecStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
