package com.sanshuiyuan.evidence.infra.repository;

import com.sanshuiyuan.evidence.domain.EvidenceOutboxEntry;
import com.sanshuiyuan.evidence.domain.EvidenceOutboxEntry.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EvidenceOutboxRepository extends JpaRepository<EvidenceOutboxEntry, Long> {

    List<EvidenceOutboxEntry> findByStatusAndNextRunAtBeforeOrderByNextRunAtAsc(
            OutboxStatus status, LocalDateTime before, Pageable pageable);

    Optional<EvidenceOutboxEntry> findByBillId(Long billId);

    @Modifying
    @Query("UPDATE EvidenceOutboxEntry e SET e.status = :status WHERE e.id = :id")
    int updateStatus(@Param("id") Long id, @Param("status") OutboxStatus status);

    @Modifying
    @Query("UPDATE EvidenceOutboxEntry e SET e.retried = :retried, e.nextRunAt = :nextRunAt, "
            + "e.status = :status WHERE e.id = :id")
    int reschedule(@Param("id") Long id, @Param("retried") int retried,
                   @Param("nextRunAt") LocalDateTime nextRunAt, @Param("status") OutboxStatus status);
}
