package com.sanshuiyuan.matching.request.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** 撮合配置 KV（matching_config，落 core_db）。 */
@Entity
@Table(name = "matching_config")
public class MatchingConfig {

    @Id
    @Column(name = "config_key", length = 64)
    private String configKey;

    @Column(name = "config_value", nullable = false, length = 255)
    private String configValue;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public String getConfigKey() { return configKey; }
    public void setConfigKey(String configKey) { this.configKey = configKey; }

    public String getConfigValue() { return configValue; }
    public void setConfigValue(String configValue) { this.configValue = configValue; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
