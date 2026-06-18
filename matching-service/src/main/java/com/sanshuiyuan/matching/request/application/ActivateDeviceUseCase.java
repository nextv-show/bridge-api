package com.sanshuiyuan.matching.request.application;

import com.sanshuiyuan.matching.asset.DeviceAssetStageEvent;
import com.sanshuiyuan.matching.asset.DeviceAssetStageEventRepository;
import com.sanshuiyuan.matching.request.api.dto.ActivateResponse;
import com.sanshuiyuan.matching.request.domain.DeviceStage;
import com.sanshuiyuan.matching.request.infra.DeviceAssetGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 029 设备激活：iot-gateway 在设备首个心跳/上线边沿调本用例（经 S2S）。
 *
 * <p>CAS 推进 device_assets.stage：{@code PENDING_ACTIVATE → STAGE_1}（设备真实联网 = 激活，分成起点）。
 * stage 转移仍由 matching 单一所有者写入（与 fulfill 同源，避免多写者竞态/跨服务越权）。
 *
 * <p><b>幂等</b>：CAS 命中 0 行（已 STAGE_1+ / 未履约 / 无此 SN）即 no-op，返回 {@code activated=false}，
 * 不报错——设备上线抖动/重连会重复触发。
 */
@Service
public class ActivateDeviceUseCase {

    private static final Logger log = LoggerFactory.getLogger(ActivateDeviceUseCase.class);

    private final DeviceAssetGateway deviceAssetGateway;
    private final DeviceAssetStageEventRepository stageEventRepository;
    private final MatchingMetrics metrics;
    private final OrderSnBindbackNotifier snBindbackNotifier;

    public ActivateDeviceUseCase(DeviceAssetGateway deviceAssetGateway,
                                 DeviceAssetStageEventRepository stageEventRepository,
                                 MatchingMetrics metrics,
                                 OrderSnBindbackNotifier snBindbackNotifier) {
        this.deviceAssetGateway = deviceAssetGateway;
        this.stageEventRepository = stageEventRepository;
        this.metrics = metrics;
        this.snBindbackNotifier = snBindbackNotifier;
    }

    @Transactional
    public ActivateResponse activate(String sn) {
        if (sn == null || sn.isBlank()) {
            metrics.activateNoop();
            return new ActivateResponse(sn, false);
        }

        int updated = deviceAssetGateway.activateBySn(
                sn, DeviceStage.PENDING_ACTIVATE, DeviceStage.STAGE_1);
        if (updated != 1) {
            // 幂等：已 STAGE_1+ / 未履约（非 PENDING_ACTIVATE）/ 无此 SN —— no-op，不报错。
            log.info("Activate: sn={} 非 PENDING_ACTIVATE 或无此设备，幂等 no-op", sn);
            metrics.activateNoop();
            return new ActivateResponse(sn, false);
        }

        Long deviceAssetId = deviceAssetGateway.findIdBySn(sn);
        DeviceAssetStageEvent event = new DeviceAssetStageEvent();
        event.setDeviceAssetId(deviceAssetId);
        event.setEventType("STAGE_1_ACTIVATED");
        event.setPayloadJson("{\"sn\":\"" + sn + "\"}");
        stageEventRepository.saveAndFlush(event);

        log.info("Activate: sn={} device_asset_id={} PENDING_ACTIVATE → STAGE_1", sn, deviceAssetId);
        metrics.activated();

        // 兜底 SN 回写（设备激活 = SN 100% 已知）。notifier 内部全包裹异常，绝不影响本事务。
        if (deviceAssetId != null) {
            snBindbackNotifier.notifyBindSn(deviceAssetId, sn);
        }
        return new ActivateResponse(sn, true);
    }
}
