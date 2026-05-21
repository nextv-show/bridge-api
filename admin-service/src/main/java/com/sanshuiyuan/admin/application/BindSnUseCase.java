package com.sanshuiyuan.admin.application;

import com.sanshuiyuan.admin.config.SecurityConfig;
import com.sanshuiyuan.admin.domain.DeviceAsset;
import com.sanshuiyuan.admin.infra.repository.DeviceAssetRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
public class BindSnUseCase {

    private final DeviceAssetRepository deviceAssetRepo;
    private final AuditLogService auditLog;

    public BindSnUseCase(DeviceAssetRepository deviceAssetRepo, AuditLogService auditLog) {
        this.deviceAssetRepo = deviceAssetRepo;
        this.auditLog = auditLog;
    }

    @Transactional
    public void bindSn(Long adminId, Long deviceAssetId, String sn) {
        if (sn == null || sn.isBlank()) {
            throw new IllegalArgumentException("SN 不能为空");
        }
        sn = sn.trim();

        var asset = deviceAssetRepo.findById(deviceAssetId)
                .orElseThrow(() -> new IllegalArgumentException("设备资产不存在: " + deviceAssetId));

        if (asset.getSn() != null) {
            throw new IllegalStateException("设备已绑定 SN: " + asset.getSn());
        }
        if (asset.getStage() != DeviceAsset.Stage.PENDING_MATCH) {
            throw new IllegalStateException("设备状态不允许绑定 SN，当前: " + asset.getStage());
        }
        if (deviceAssetRepo.existsBySn(sn)) {
            throw new IllegalStateException("SN 已被使用: " + sn);
        }

        asset.setSn(sn);
        deviceAssetRepo.save(asset);

        auditLog.log(adminId, "BIND_SN", "device_asset",
                String.valueOf(deviceAssetId),
                "{\"sn\":\"" + sn + "\"}");
    }
}
