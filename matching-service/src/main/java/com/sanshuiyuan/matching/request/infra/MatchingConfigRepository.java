package com.sanshuiyuan.matching.request.infra;

import com.sanshuiyuan.matching.request.domain.MatchingConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchingConfigRepository extends JpaRepository<MatchingConfig, String> {
}
