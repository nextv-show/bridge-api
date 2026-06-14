package com.sanshuiyuan.logistics.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanshuiyuan.logistics.domain.LogisticsOrder;
import com.sanshuiyuan.logistics.domain.LogisticsStatus;
import com.sanshuiyuan.logistics.infra.LogisticsOrderRepository;
import com.sanshuiyuan.logistics.infra.OutboxReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * D.2 Outbox 消费者：每 5s 扫描 matching 库的 logistics_outbox（同 core_db），
 * consumed_at IS NULL 的记录 → 创建 PENDING_SHIP 工单 → 标记 consumed_at。
 */
@Component
public class LogisticsOutboxConsumer {

    private static final Logger log = LoggerFactory.getLogger(LogisticsOutboxConsumer.class);

    private final OutboxReader outboxReader;
    private final LogisticsOrderRepository orderRepository;

    public LogisticsOutboxConsumer(OutboxReader outboxReader,
                                   LogisticsOrderRepository orderRepository) {
        this.outboxReader = outboxReader;
        this.orderRepository = orderRepository;
    }

    @Scheduled(fixedDelay = 5000)
    public void poll() {
        var batch = outboxReader.fetchPending();
        if (batch.isEmpty()) {
            return;
        }
        log.info("OutboxConsumer: 拉取到 {} 条待处理记录", batch.size());
        for (var row : batch) {
            processOne(row);
        }
    }

    @Transactional
    @Retryable(maxAttempts = 5, backoff = @Backoff(
            delay = 2000, multiplier = 2.0, maxDelay = 30000))
    public void processOne(Map<String, Object> row) {
        long outboxId = ((Number) row.get("id")).longValue();
        Object reqIdObj = row.get("request_id");
        Long requestId = reqIdObj != null ? ((Number) reqIdObj).longValue() : null;
        long deviceAssetId = ((Number) row.get("device_asset_id")).longValue();
        String payloadJson = (String) row.get("payload_json");
        String source = (String) row.get("source");

        // MATCHING 场景：幂等检查（同一 request_id 已存在工单则跳过）。
        // SELF_USE 场景 request_id 为 null，无幂等键，跳过此检查（用工单 device_asset_id 锚定）。
        if (requestId != null && orderRepository.existsByRequestId(requestId)) {
            log.info("OutboxConsumer: request_id={} 已有工单，标记 consumed 并跳过", requestId);
            outboxReader.markConsumed(outboxId);
            return;
        }

        try {
            LogisticsOrder order = new LogisticsOrder();
            order.setRequestId(requestId);   // SELF_USE 场景为 null
            order.setDeviceAssetId(deviceAssetId);
            order.setShipToAddressSnapshot(payloadJson);
            order.setStatus(LogisticsStatus.PENDING_SHIP);
            orderRepository.saveAndFlush(order);

            // 事务成功后标记 outbox consumed
            outboxReader.markConsumed(outboxId);
            log.info("OutboxConsumer: 创建物流工单 orderId={} requestId={} deviceId={} source={}",
                    order.getId(), requestId, deviceAssetId, source);
        } catch (DataIntegrityViolationException e) {
            // uk_request 冲突（并发/重试），标记 consumed 并忽略
            log.warn("OutboxConsumer: 工单冲突 requestId={} deviceId={}, 标记 consumed", requestId, deviceAssetId);
            outboxReader.markConsumed(outboxId);
        }
    }
}
