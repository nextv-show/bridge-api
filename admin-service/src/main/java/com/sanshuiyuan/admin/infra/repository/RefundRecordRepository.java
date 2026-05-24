package com.sanshuiyuan.admin.infra.repository;

import com.sanshuiyuan.admin.domain.RefundRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRecordRepository extends JpaRepository<RefundRecord, Long> {

    Page<RefundRecord> findByStatus(RefundRecord.Status status, Pageable pageable);

    Page<RefundRecord> findByRefundType(RefundRecord.RefundType refundType, Pageable pageable);

    Page<RefundRecord> findByStatusAndRefundType(RefundRecord.Status status,
                                                  RefundRecord.RefundType refundType,
                                                  Pageable pageable);

    Page<RefundRecord> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(RefundRecord.Status status);
}
