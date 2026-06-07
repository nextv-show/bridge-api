package com.sanshuiyuan.evidence.application;

import com.sanshuiyuan.evidence.domain.EvidenceOutboxEntry;
import com.sanshuiyuan.evidence.domain.EvidenceOutboxEntry.OutboxStatus;
import com.sanshuiyuan.evidence.infra.antchain.AntChainClient;
import com.sanshuiyuan.evidence.infra.repository.EvidenceOutboxRepository;
import com.sanshuiyuan.evidence.infra.repository.WaterBillRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

/**
 * 单条存证发件箱记录的上链用例：SHA-256(payload) → 上链 → 成功置 DONE 并回写账单；
 * 失败按退避策略重排，超过最大次数触发告警终态化。chain_status 为最终一致，不阻塞主交易。
 */
@Service
public class SubmitEvidenceUseCase {

    private static final Logger log = LoggerFactory.getLogger(SubmitEvidenceUseCase.class);

    private final EvidenceOutboxRepository outboxRepo;
    private final WaterBillRepository billRepo;
    private final AntChainClient antChainClient;
    private final BackoffRetryPolicy backoffRetry;
    private final EvidenceFailureAlerter failureAlerter;

    public SubmitEvidenceUseCase(EvidenceOutboxRepository outboxRepo,
                                 WaterBillRepository billRepo,
                                 AntChainClient antChainClient,
                                 BackoffRetryPolicy backoffRetry,
                                 EvidenceFailureAlerter failureAlerter) {
        this.outboxRepo = outboxRepo;
        this.billRepo = billRepo;
        this.antChainClient = antChainClient;
        this.backoffRetry = backoffRetry;
        this.failureAlerter = failureAlerter;
    }

    /** 处理单条发件箱记录：成功上链或按退避重排/告警。 */
    @Transactional
    public void process(EvidenceOutboxEntry entry) {
        Long outboxId = entry.getId();
        Long billId = entry.getBillId();
        try {
            String hashHex = sha256Hex(entry.getPayloadJson());
            String txHash = antChainClient.submit(hashHex);

            outboxRepo.updateStatus(outboxId, OutboxStatus.DONE);
            billRepo.updateOnChain(billId, txHash, entry.getRetried());
            log.info("Evidence on-chain OK: outboxId={}, billId={}, txHash={}", outboxId, billId, txHash);
        } catch (Exception e) {
            int nextRetried = entry.getRetried() + 1;
            Optional<Duration> delay = backoffRetry.nextDelay(entry.getRetried());
            if (delay.isPresent()) {
                LocalDateTime nextRunAt = LocalDateTime.now().plus(delay.get());
                outboxRepo.reschedule(outboxId, nextRetried, nextRunAt, OutboxStatus.PENDING);
                log.warn("Evidence on-chain failed, will retry: outboxId={}, billId={}, retried={}, nextRunAt={}, error={}",
                        outboxId, billId, nextRetried, nextRunAt, e.toString());
            } else {
                failureAlerter.alert(billId, outboxId, e.toString());
            }
        }
    }

    private static String sha256Hex(String payload) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
