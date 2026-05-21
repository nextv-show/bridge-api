package com.sanshuiyuan.admin.infra.repository;

import com.sanshuiyuan.admin.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
