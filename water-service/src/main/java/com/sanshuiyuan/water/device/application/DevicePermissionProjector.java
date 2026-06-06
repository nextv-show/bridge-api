package com.sanshuiyuan.water.device.application;

import com.sanshuiyuan.water.device.domain.DevicePermission;
import com.sanshuiyuan.water.device.domain.LockedReason;
import com.sanshuiyuan.water.device.infra.DevicePermissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 设备权限投影。最终由 002 上游 outbox 驱动（schema 冻结后开启 @Scheduled 轮询）；
 * 当前先暴露可被直接调用的投影方法。
 */
@Service
public class DevicePermissionProjector {

    private static final Logger log = LoggerFactory.getLogger(DevicePermissionProjector.class);

    private final DevicePermissionRepository repo;

    public DevicePermissionProjector(DevicePermissionRepository repo) {
        this.repo = repo;
    }

    // @Scheduled(fixedDelay = 5000) — 002 outbox 表结构冻结后开启
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
}
