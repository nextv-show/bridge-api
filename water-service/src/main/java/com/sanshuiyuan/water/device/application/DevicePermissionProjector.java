package com.sanshuiyuan.water.device.application;

import com.sanshuiyuan.water.device.domain.DeviceControlEvent;
import com.sanshuiyuan.water.device.domain.DevicePermission;
import com.sanshuiyuan.water.device.domain.LockedReason;
import com.sanshuiyuan.water.device.infra.DeviceControlEventRepository;
import com.sanshuiyuan.water.device.infra.DevicePermissionRepository;
import com.sanshuiyuan.water.device.infra.IotGatewayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备权限投影。轮询 device_control_events（mock 控制面 outbox）消费 LOCK_CHANGED 事件，
 * 更新 device_permissions 并经 iot-gateway 下发 MQTT 锁定/解锁命令。
 */
@Service
public class DevicePermissionProjector {

    private static final Logger log = LoggerFactory.getLogger(DevicePermissionProjector.class);

    private final DevicePermissionRepository repo;
    private final DeviceControlEventRepository controlEventRepo;
    private final IotGatewayClient iotGatewayClient;

    public DevicePermissionProjector(DevicePermissionRepository repo,
                                     DeviceControlEventRepository controlEventRepo,
                                     IotGatewayClient iotGatewayClient) {
        this.repo = repo;
        this.controlEventRepo = controlEventRepo;
        this.iotGatewayClient = iotGatewayClient;
    }

    /** 设备安装完成：允许出水。 */
    public void onInstalled(String sn) {
        var perm = repo.findBySn(sn).orElseGet(() -> new DevicePermission(sn));
        perm.setCanDispense(true);
        perm.setLockedReason(null);
        repo.save(perm);
        log.info("[Permission] SN={} set can_dispense=true (INSTALLED)", sn);
    }

    /** 锁定状态变更：按 canDispense 设置出水权限，禁用时记录原因。 */
    public void onLockChanged(String sn, boolean canDispense, String reason) {
        var perm = repo.findBySn(sn).orElseGet(() -> new DevicePermission(sn));
        perm.setCanDispense(canDispense);
        perm.setLockedReason(canDispense ? null : LockedReason.valueOf(reason));
        repo.save(perm);
        log.info("[Permission] SN={} set can_dispense={} reason={}", sn, canDispense, reason);
    }

    /**
     * 轮询 device_control_events 的 LOCK_CHANGED 事件。
     * 锁定：can_dispense=false 并下发 MQTT cmd/lock；解锁：can_dispense=true 并下发 MQTT cmd/unlock。
     * 单条失败不标记消费，下个周期重试。
     */
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void pollLockChangedEvents() {
        List<DeviceControlEvent> events = controlEventRepo
            .findByEventTypeAndConsumedByWaterIsNullOrderByCreatedAtAsc("LOCK_CHANGED");

        for (DeviceControlEvent event : events) {
            try {
                String sn = event.getSn();
                boolean canDispense = event.isCanDispense();
                String reason = event.getReason();

                // 更新 device_permissions
                var perm = repo.findBySn(sn).orElseGet(() -> new DevicePermission(sn));
                perm.setCanDispense(canDispense);
                perm.setLockedReason(canDispense ? null : LockedReason.LOCKED);
                repo.save(perm);

                // 经 iot-gateway 下发 MQTT 命令
                if (canDispense) {
                    iotGatewayClient.unlock(sn);
                    log.info("[Permission] SN={} UNLOCKED via control event", sn);
                } else {
                    iotGatewayClient.lock(sn, reason != null ? reason : "LOCKED");
                    log.info("[Permission] SN={} LOCKED via control event reason={}", sn, reason);
                }

                // 标记已消费
                event.setConsumedByWater(LocalDateTime.now());
                controlEventRepo.save(event);

            } catch (Exception e) {
                log.error("[Permission] Failed to process control event id={}: {}", event.getId(), e.getMessage());
                // 不标记消费 — 下个周期重试
            }
        }

        if (!events.isEmpty()) {
            log.info("[Permission] Processed {} LOCK_CHANGED events", events.size());
        }
    }
}
