package com.sanshuiyuan.water.session.infra;

import com.sanshuiyuan.water.session.domain.EvidenceOutboxEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EvidenceOutboxRepository extends JpaRepository<EvidenceOutboxEntry, Long> {

    List<EvidenceOutboxEntry> findByStatusAndNextRunAtBeforeOrderByNextRunAtAsc(String status, LocalDateTime before);

    Optional<EvidenceOutboxEntry> findByBillId(Long billId);
}
