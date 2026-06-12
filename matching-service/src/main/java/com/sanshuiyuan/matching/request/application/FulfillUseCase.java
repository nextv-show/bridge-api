package com.sanshuiyuan.matching.request.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanshuiyuan.matching.asset.DeviceAssetStageEvent;
import com.sanshuiyuan.matching.asset.DeviceAssetStageEventRepository;
import com.sanshuiyuan.matching.request.api.ApiException;
import com.sanshuiyuan.matching.request.domain.DeviceStage;
import com.sanshuiyuan.matching.request.domain.MatchingRequest;
import com.sanshuiyuan.matching.request.domain.RequestStatus;
import com.sanshuiyuan.matching.request.infra.DeviceAssetGateway;
import com.sanshuiyuan.matching.request.infra.MatchingRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * D.4 INSTALLED 跨服务联动：物流安装完成后，matching 侧完成需求履约 + 设备阶段推进。
 * <ol>
 *   <li>matching_requests.status = FULFILLED</li>
 *   <li>device_assets.stage = PENDING_ACTIVATE（受限网关 CAS）</li>
 *   <li>写 device_assets_stage_events(type=PENDING_ACTIVATE_READY)</li>
 * </ol>
 * 单事务内完成，保证一致性。
 */
@Service
public class FulfillUseCase {

    private static final Logger log = LoggerFactory.getLogger(FulfillUseCase.class);

    private final MatchingRequestRepository requestRepository;
    private final DeviceAssetGateway deviceAssetGateway;
    private final DeviceAssetStageEventRepository stageEventRepository;
    private final MatchingMetrics metrics;
    private final ObjectMapper objectMapper;

    public FulfillUseCase(MatchingRequestRepository requestRepository,
                          DeviceAssetGateway deviceAssetGateway,
                          DeviceAssetStageEventRepository stageEventRepository,
                          MatchingMetrics metrics,
                          ObjectMapper objectMapper) {
        this.requestRepository = requestRepository;
        this.deviceAssetGateway = deviceAssetGateway;
        this.stageEventRepository = stageEventRepository;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行 fulfillment。幂等：已 FULFILLED 的需求直接返回。
     */
    @Transactional
    public void fulfill(long requestId, long deviceAssetId, long logisticsOrderId) {
        MatchingRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("REQUEST_NOT_FOUND", "需求不存在"));

        if (request.getStatus() == RequestStatus.FULFILLED) {
            log.info("Fulfill: request_id={} 已 FULFILLED，幂等跳过", requestId);
            return;
        }

        if (request.getStatus() != RequestStatus.LOCKED) {
            throw ApiException.conflict("REQUEST_NOT_LOCKED",
                    "需求状态不是 LOCKED，无法推进到 FULFILLED");
        }

        // 1) matching_requests.status = FULFILLED
        request.setStatus(RequestStatus.FULFILLED);
        requestRepository.saveAndFlush(request);
        log.info("Fulfill: request_id={} → FULFILLED", requestId);

        // 2) device_assets.stage = PENDING_ACTIVATE
        long ownerUserId = request.getLockedByUserId() != null ? request.getLockedByUserId() : 0L;
        int updated = deviceAssetGateway.advanceStage(
                deviceAssetId, ownerUserId, DeviceStage.LOCKED, DeviceStage.PENDING_ACTIVATE);
        if (updated != 1) {
            throw ApiException.conflict("DEVICE_STAGE_INVALID",
                    "设备状态不允许推进到 PENDING_ACTIVATE（当前非 LOCKED 或归属不符）");
        }
        log.info("Fulfill: device_asset_id={} → PENDING_ACTIVATE", deviceAssetId);

        // 3) 写 device_assets_stage_events
        DeviceAssetStageEvent event = new DeviceAssetStageEvent();
        event.setDeviceAssetId(deviceAssetId);
        event.setEventType("PENDING_ACTIVATE_READY");
        event.setPayloadJson(buildPayload(requestId, logisticsOrderId));
        stageEventRepository.saveAndFlush(event);
        log.info("Fulfill: 写 device_assets_stage_events device={} type=PENDING_ACTIVATE_READY", deviceAssetId);

        metrics.fulfilled();   // P1-5 最终激活前置埋点（仅真正履约计数，幂等跳过不计）
    }

    private String buildPayload(long requestId, long logisticsOrderId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("request_id", requestId);
        payload.put("logistics_order_id", logisticsOrderId);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("构造 stage event payload 失败", e);
        }
    }
}
