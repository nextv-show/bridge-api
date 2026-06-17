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
    private final OrderSnBindbackNotifier snBindbackNotifier;

    public FulfillUseCase(MatchingRequestRepository requestRepository,
                          DeviceAssetGateway deviceAssetGateway,
                          DeviceAssetStageEventRepository stageEventRepository,
                          MatchingMetrics metrics,
                          ObjectMapper objectMapper,
                          OrderSnBindbackNotifier snBindbackNotifier) {
        this.requestRepository = requestRepository;
        this.deviceAssetGateway = deviceAssetGateway;
        this.stageEventRepository = stageEventRepository;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
        this.snBindbackNotifier = snBindbackNotifier;
    }

    /**
     * 执行 fulfillment。幂等：已 FULFILLED 的需求直接返回。
     *
     * <p>{@code requestId == null} 表示 SELF_USE 设备（无匹配需求），转调 {@link #fulfillSelfUse}。
     */
    @Transactional
    public void fulfill(Long requestId, long deviceAssetId, long logisticsOrderId) {
        if (requestId == null) {
            fulfillSelfUse(deviceAssetId, logisticsOrderId);
            return;
        }

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

        // best-effort SN 回写（设备已安装，SN 可能已被 admin 绑定）
        tryBindSn(deviceAssetId);

        // 3) 写 device_assets_stage_events
        DeviceAssetStageEvent event = new DeviceAssetStageEvent();
        event.setDeviceAssetId(deviceAssetId);
        event.setEventType("PENDING_ACTIVATE_READY");
        event.setPayloadJson(buildPayload(requestId, logisticsOrderId));
        stageEventRepository.saveAndFlush(event);
        log.info("Fulfill: 写 device_assets_stage_events device={} type=PENDING_ACTIVATE_READY", deviceAssetId);

        metrics.fulfilled();   // P1-5 最终激活前置埋点（仅真正履约计数，幂等跳过不计）
    }

    /**
     * SELF_USE 设备履约：无匹配需求，仅推进设备 SELF_USE → PENDING_ACTIVATE 并写 stage event。
     * 幂等：设备已处于 PENDING_ACTIVATE（重试）直接返回，不重复计数。
     */
    @Transactional
    public void fulfillSelfUse(long deviceAssetId, long logisticsOrderId) {
        // 正向校验：设备确实处于 SELF_USE 阶段，防止普通撮合单丢 requestId 后误入此路径。
        // 与下方 CAS 语义双重保证：普通撮合单设备处于 LOCKED 而非 SELF_USE，此处即被拒绝。
        // 例外：设备已 PENDING_ACTIVATE 时放行，交由下方 CAS 失败分支做幂等处理。
        String currentStage = deviceAssetGateway.findStage(deviceAssetId);
        if (!DeviceStage.SELF_USE.name().equals(currentStage)
                && !DeviceStage.PENDING_ACTIVATE.name().equals(currentStage)) {
            throw ApiException.conflict("DEVICE_STAGE_INVALID",
                    "非 SELF_USE 设备不允许走 self-use fulfill（当前: " + currentStage + "）");
        }

        // device_assets.stage = PENDING_ACTIVATE（CAS：仅 SELF_USE → PENDING_ACTIVATE）
        int updated = deviceAssetGateway.advanceStageByDevice(
                deviceAssetId, DeviceStage.SELF_USE, DeviceStage.PENDING_ACTIVATE);
        if (updated != 1) {
            String current = deviceAssetGateway.findStage(deviceAssetId);
            if (DeviceStage.PENDING_ACTIVATE.name().equals(current)) {
                log.info("FulfillSelfUse: device_asset_id={} 已 PENDING_ACTIVATE，幂等跳过", deviceAssetId);
                return;
            }
            throw ApiException.conflict("DEVICE_STAGE_INVALID",
                    "设备状态不允许推进到 PENDING_ACTIVATE（当前非 SELF_USE）");
        }
        log.info("FulfillSelfUse: device_asset_id={} SELF_USE → PENDING_ACTIVATE", deviceAssetId);

        // best-effort SN 回写（设备已安装，SN 可能已被 admin 绑定）
        tryBindSn(deviceAssetId);

        // 写 device_assets_stage_events（payload 标记 source=SELF_USE，无 request_id）
        DeviceAssetStageEvent event = new DeviceAssetStageEvent();
        event.setDeviceAssetId(deviceAssetId);
        event.setEventType("PENDING_ACTIVATE_READY");
        event.setPayloadJson(buildSelfUsePayload(logisticsOrderId));
        stageEventRepository.saveAndFlush(event);
        log.info("FulfillSelfUse: 写 device_assets_stage_events device={} type=PENDING_ACTIVATE_READY source=SELF_USE",
                deviceAssetId);

        metrics.fulfilled();   // P1-5 最终激活前置埋点（仅真正履约计数，幂等跳过不计）
    }

    /**
     * 110 best-effort SN 回写：履约推进成功后，若 device_assets.sn 已是真实 SN（admin 已绑定），
     * 通知 cend 回写到 h5_orders。notifier 内部全包裹异常，绝不影响本事务。
     */
    private void tryBindSn(long deviceAssetId) {
        String sn = deviceAssetGateway.findSnById(deviceAssetId);
        if (sn != null && !sn.isBlank() && !sn.startsWith("SN-PENDING")) {
            snBindbackNotifier.notifyBindSn(deviceAssetId, sn);
        }
    }

    private String buildPayload(long requestId, long logisticsOrderId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("request_id", requestId);
        payload.put("logistics_order_id", logisticsOrderId);
        return serialize(payload);
    }

    private String buildSelfUsePayload(long logisticsOrderId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", "SELF_USE");
        payload.put("logistics_order_id", logisticsOrderId);
        return serialize(payload);
    }

    private String serialize(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("构造 stage event payload 失败", e);
        }
    }
}
