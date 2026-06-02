package com.sanshuiyuan.logistics.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanshuiyuan.logistics.api.LogisticsApiException;
import com.sanshuiyuan.logistics.domain.LogisticsEvent;
import com.sanshuiyuan.logistics.domain.LogisticsOrder;
import com.sanshuiyuan.logistics.domain.LogisticsStatus;
import com.sanshuiyuan.logistics.infra.LogisticsEventRepository;
import com.sanshuiyuan.logistics.infra.LogisticsOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 物流工单状态机：PENDING_SHIP → SHIPPED → DELIVERED → INSTALLED。
 * 非法转移抛 422；每次推进写 logistics_events。
 */
@Service
public class AdvanceStateUseCase {

    private static final Logger log = LoggerFactory.getLogger(AdvanceStateUseCase.class);

    /** 合法转移图 */
    private static final Map<LogisticsStatus, Set<LogisticsStatus>> ALLOWED =
            Map.of(
                    LogisticsStatus.PENDING_SHIP, Set.of(LogisticsStatus.SHIPPED),
                    LogisticsStatus.SHIPPED, Set.of(LogisticsStatus.DELIVERED),
                    LogisticsStatus.DELIVERED, Set.of(LogisticsStatus.INSTALLED)
            );

    private final LogisticsOrderRepository orderRepository;
    private final LogisticsEventRepository eventRepository;
    private final InstalledEventPublisher installedEventPublisher;

    public AdvanceStateUseCase(LogisticsOrderRepository orderRepository,
                               LogisticsEventRepository eventRepository,
                               InstalledEventPublisher installedEventPublisher) {
        this.orderRepository = orderRepository;
        this.eventRepository = eventRepository;
        this.installedEventPublisher = installedEventPublisher;
    }

    /**
     * 推进状态；externalEventId 可选（webhook 场景用于幂等）。
     * 事务内：校验状态机合法 → 更新 order.status → 写 event → 若 INSTALLED 则事务提交后调 matching fulfill。
     */
    @Transactional
    public LogisticsOrder advance(long orderId, LogisticsStatus toStatus,
                                   String note, String externalEventId) {
        LogisticsOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> LogisticsApiException.notFound(
                        "ORDER_NOT_FOUND", "物流工单不存在"));

        // 幂等检查（webhook 重放）
        if (externalEventId != null && eventRepository.existsByExternalEventId(externalEventId)) {
            log.info("物流事件幂等：external_event_id={} 已处理，跳过 order={}", externalEventId, orderId);
            return order;
        }

        Set<LogisticsStatus> allowed = ALLOWED.get(order.getStatus());
        if (allowed == null || !allowed.contains(toStatus)) {
            throw LogisticsApiException.unprocessable(
                    "INVALID_TRANSITION",
                    String.format("状态不能从 %s 转到 %s", order.getStatus(), toStatus));
        }

        order.setStatus(toStatus);
        orderRepository.saveAndFlush(order);

        LogisticsEvent event = new LogisticsEvent();
        event.setLogisticsOrderId(orderId);
        event.setEventType(toStatus.name());
        event.setPayloadJson(note != null ? "{\"note\":\"" + escapeJson(note) + "\"}" : null);
        event.setExternalEventId(externalEventId);
        eventRepository.saveAndFlush(event);

        log.info("物流工单 {} 状态 {} → {} (external_event_id={})",
                orderId, order.getStatus().name().equals(toStatus.name()) ? toStatus : order.getStatus(),
                toStatus, externalEventId);

        if (toStatus == LogisticsStatus.INSTALLED) {
            installedEventPublisher.publishAfterCommit(order);
        }

        return order;
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
