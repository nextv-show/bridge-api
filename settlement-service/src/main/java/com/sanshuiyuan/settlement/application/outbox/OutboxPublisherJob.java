package com.sanshuiyuan.settlement.application.outbox;

import com.sanshuiyuan.settlement.domain.OutboxEventType;
import com.sanshuiyuan.settlement.domain.OutboxStatus;
import com.sanshuiyuan.settlement.domain.SettlementOutbox;
import com.sanshuiyuan.settlement.infra.repository.SettlementOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class OutboxPublisherJob {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisherJob.class);
    private static final int MAX_RETRIES = 10;

    private final SettlementOutboxRepository outboxRepository;
    private final RestTemplate restTemplate;
    private final String waterServiceBaseUrl;

    public OutboxPublisherJob(SettlementOutboxRepository outboxRepository,
                              @Value("${water-service.base-url:http://localhost:8088}") String waterServiceBaseUrl) {
        this.outboxRepository = outboxRepository;
        this.restTemplate = new RestTemplate();
        this.waterServiceBaseUrl = waterServiceBaseUrl;
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publish() {
        List<SettlementOutbox> pending = outboxRepository
                .findByStatusAndNextRunAtBefore(OutboxStatus.PENDING, LocalDateTime.now());

        for (SettlementOutbox outbox : pending) {
            try {
                if (outbox.getEventType() == OutboxEventType.STAGE_CHANGED) {
                    publishStageChanged(outbox);
                } else if (outbox.getEventType() == OutboxEventType.ENTRY_POSTED) {
                    // V1: 仅写日志，等 005 拉取
                    log.info("[outbox] ENTRY_POSTED id={} aggregateId={}", outbox.getId(), outbox.getAggregateId());
                    markPublished(outbox);
                } else if (outbox.getEventType() == OutboxEventType.RECONCILE_FAILED) {
                    // V1: 仅写日志，等 005 拉取
                    log.warn("[outbox] RECONCILE_FAILED id={} aggregateId={}", outbox.getId(), outbox.getAggregateId());
                    markPublished(outbox);
                }
            } catch (Exception e) {
                handleRetry(outbox, e);
            }
        }
    }

    private void publishStageChanged(SettlementOutbox outbox) {
        String url = waterServiceBaseUrl + "/internal/settlement/stage-changed";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // TODO Phase C.2: 后续实现 S2S token header，当前 003 只落日志接受任何请求
        // headers.setBearerAuth(s2sToken);

        HttpEntity<String> request = new HttpEntity<>(outbox.getPayloadJson(), headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("[outbox] STAGE_CHANGED published id={} sn={}", outbox.getId(), outbox.getAggregateId());
                markPublished(outbox);
            } else {
                throw new RuntimeException("Non-2xx response: " + response.getStatusCode());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish STAGE_CHANGED to water-service", e);
        }
    }

    private void markPublished(SettlementOutbox outbox) {
        outbox.setStatus(OutboxStatus.PUBLISHED);
        outbox.setPublishedAt(LocalDateTime.now());
        outboxRepository.save(outbox);
    }

    private void handleRetry(SettlementOutbox outbox, Exception e) {
        int retries = outbox.getRetried() + 1;
        outbox.setRetried(retries);

        if (retries >= MAX_RETRIES) {
            outbox.setStatus(OutboxStatus.FAILED);
            log.error("[outbox] FAILED after {} retries id={} type={} aggregateId={}",
                    retries, outbox.getId(), outbox.getEventType(), outbox.getAggregateId(), e);
        } else {
            // 指数退避: 1s, 2s, 4s, 8s, ... up to ~8min
            long backoffSeconds = Math.min(1L << retries, 512);
            outbox.setNextRunAt(LocalDateTime.now().plusSeconds(backoffSeconds));
            log.warn("[outbox] retry {} id={} type={} nextRunAt={}s",
                    retries, outbox.getId(), outbox.getEventType(), backoffSeconds);
        }
        outboxRepository.save(outbox);
    }
}
