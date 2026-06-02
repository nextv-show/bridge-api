package com.sanshuiyuan.cend.infra.repository;

import com.sanshuiyuan.cend.domain.ConfigStatus;
import com.sanshuiyuan.cend.domain.LandingConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LandingConfigRepository extends JpaRepository<LandingConfig, Long> {

    /** 取最新一条生效配置（同一时刻应仅一条 PUBLISHED，按 published_at 兜底排序取首条）。 */
    Optional<LandingConfig> findFirstByStatusOrderByPublishedAtDesc(ConfigStatus status);
}
