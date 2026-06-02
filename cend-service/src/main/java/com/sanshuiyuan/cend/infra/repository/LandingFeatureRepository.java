package com.sanshuiyuan.cend.infra.repository;

import com.sanshuiyuan.cend.domain.LandingFeature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LandingFeatureRepository extends JpaRepository<LandingFeature, Long> {

    /** 一次性按 sort 拉取某配置的全部 feature（避免 N+1）。 */
    List<LandingFeature> findByConfigIdOrderBySortAsc(Long configId);
}
