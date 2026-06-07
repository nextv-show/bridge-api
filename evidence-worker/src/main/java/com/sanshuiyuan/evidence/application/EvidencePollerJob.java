package com.sanshuiyuan.evidence.application;

import com.sanshuiyuan.evidence.domain.EvidenceOutboxEntry;
import com.sanshuiyuan.evidence.domain.EvidenceOutboxEntry.OutboxStatus;
import com.sanshuiyuan.evidence.infra.repository.EvidenceOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 存证发件箱轮询：每 2s 取一批到期的 PENDING 记录逐条上链。
 * 每条记录由 SubmitEvidenceUseCase 独立事务处理，单条失败不影响其余。
 */
@Component
public class EvidencePollerJob {

    private static final Logger log = LoggerFactory.getLogger(EvidencePollerJob.class);
    private static final int BATCH_SIZE = 100;

    private final EvidenceOutboxRepository outboxRepo;
    private final SubmitEvidenceUseCase submitEvidenceUseCase;

    public EvidencePollerJob(EvidenceOutboxRepository outboxRepo, SubmitEvidenceUseCase submitEvidenceUseCase) {
        this.outboxRepo = outboxRepo;
        this.submitEvidenceUseCase = submitEvidenceUseCase;
    }

    @Scheduled(fixedDelay = 2000) // 2s
    public void poll() {
        List<EvidenceOutboxEntry> pending = outboxRepo.findByStatusAndNextRunAtBeforeOrderByNextRunAtAsc(
                OutboxStatus.PENDING, LocalDateTime.now(), PageRequest.of(0, BATCH_SIZE));
        for (EvidenceOutboxEntry entry : pending) {
            try {
                submitEvidenceUseCase.process(entry);
            } catch (Exception e) {
                log.error("Unexpected error processing outbox entry id={}: {}", entry.getId(), e.toString(), e);
            }
        }
    }
}
