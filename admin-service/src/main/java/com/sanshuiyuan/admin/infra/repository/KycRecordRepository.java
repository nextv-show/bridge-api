package com.sanshuiyuan.admin.infra.repository;

import com.sanshuiyuan.admin.domain.KycRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KycRecordRepository extends JpaRepository<KycRecord, Long> {

    Page<KycRecord> findByStatus(KycRecord.Status status, Pageable pageable);

    Page<KycRecord> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(KycRecord.Status status);
}
