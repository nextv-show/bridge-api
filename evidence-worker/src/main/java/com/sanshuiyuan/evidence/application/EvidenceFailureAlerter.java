package com.sanshuiyuan.evidence.application;

import com.sanshuiyuan.evidence.domain.EvidenceOutboxEntry.OutboxStatus;
import com.sanshuiyuan.evidence.infra.repository.EvidenceOutboxRepository;
import com.sanshuiyuan.evidence.infra.repository.WaterBillRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 存证终态失败告警：outbox 超过最大重试次数时，将发件箱与账单置 FAILED 并写日志告警。
 * V1 仅 log.error（alarms_outbox 在 core_db，本服务不直连）。
 */
@Component
public class EvidenceFailureAlerter {

    private static final Logger log = LoggerFactory.getLogger(EvidenceFailureAlerter.class);

    private final EvidenceOutboxRepository outboxRepo;
    private final WaterBillRepository billRepo;

    public EvidenceFailureAlerter(EvidenceOutboxRepository outboxRepo, WaterBillRepository billRepo) {
        this.outboxRepo = outboxRepo;
        this.billRepo = billRepo;
    }

    @Transactional
    public void alert(Long billId, Long outboxId, String lastError) {
        outboxRepo.updateStatus(outboxId, OutboxStatus.FAILED);
        billRepo.updateFailed(billId);
        log.error("Evidence on-chain FAILED after max retries: outboxId={}, billId={}, lastError={}",
                outboxId, billId, lastError);
    }
}
