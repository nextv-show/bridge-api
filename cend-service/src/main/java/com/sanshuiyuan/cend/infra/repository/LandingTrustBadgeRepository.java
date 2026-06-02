package com.sanshuiyuan.cend.infra.repository;

import com.sanshuiyuan.cend.domain.LandingTrustBadge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LandingTrustBadgeRepository extends JpaRepository<LandingTrustBadge, Long> {

    /** 一次性按 sort 拉取某配置的全部 trust badge（避免 N+1）。 */
    List<LandingTrustBadge> findByConfigIdOrderBySortAsc(Long configId);
}
