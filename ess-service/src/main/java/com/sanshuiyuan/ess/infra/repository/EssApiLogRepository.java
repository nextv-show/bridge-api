package com.sanshuiyuan.ess.infra.repository;

import com.sanshuiyuan.ess.domain.EssApiLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface EssApiLogRepository extends JpaRepository<EssApiLog, Long> {

    List<EssApiLog> findByApiActionAndCreatedAtAfter(String apiAction, LocalDateTime after);

    List<EssApiLog> findByStatusCodeAndCreatedAtAfter(Integer statusCode, LocalDateTime after);

    long countByApiActionAndCreatedAtAfter(String apiAction, LocalDateTime after);
}
