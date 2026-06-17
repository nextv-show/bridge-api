package com.sanshuiyuan.admin.application;

import com.sanshuiyuan.admin.domain.DeviceAsset;
import com.sanshuiyuan.admin.infra.repository.DeviceAssetRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 批量绑定 SN——支持指定设备模式与自动分配模式。
 *
 * <p>不使用 {@code @Transactional}：批量需要"部分成功"语义，每条 save 独立提交，失败收集到 skipped。
 * 校验逻辑与 {@link BindSnUseCase} 一致，但改为收集错误而非抛异常。
 */
@Service
public class BatchBindSnUseCase {

    /** 单次批量上限。 */
    private static final int MAX_BATCH = 200;

    /** SN 保留字前缀——系统占位 SN，不允许人工绑定。 */
    private static final String RESERVED_PREFIX = "SN-PENDING-";

    private final DeviceAssetRepository deviceAssetRepo;
    private final AuditLogService auditLog;

    public BatchBindSnUseCase(DeviceAssetRepository deviceAssetRepo, AuditLogService auditLog) {
        this.deviceAssetRepo = deviceAssetRepo;
        this.auditLog = auditLog;
    }

    /**
     * 批量绑定 SN。
     *
     * @param adminId        操作人
     * @param sns            SN 列表（trim、去空、去重后校验）
     * @param deviceAssetIds 指定设备 ID 列表；为 null/空时走自动分配模式（FIFO 取 PENDING_MATCH 未绑设备）
     * @return 绑定结果（总数 / 成功数 / 跳过列表）
     */
    public BatchBindResult batchBindSn(Long adminId, List<String> sns, List<Long> deviceAssetIds) {
        if (sns == null || sns.isEmpty()) {
            throw new IllegalArgumentException("SN 列表不能为空");
        }
        if (sns.size() > MAX_BATCH) {
            throw new IllegalArgumentException("单次批量上限 " + MAX_BATCH + " 条，当前: " + sns.size());
        }

        List<String> cleanSns = sanitizeSns(sns);
        int total = cleanSns.size();

        List<DeviceAsset> devices = resolveDevices(deviceAssetIds, cleanSns.size());

        List<SkipEntry> skipped = new ArrayList<>();
        int bound = 0;
        int matchCount = Math.min(cleanSns.size(), devices.size());

        for (int i = 0; i < cleanSns.size(); i++) {
            String sn = cleanSns.get(i);

            // SN 数量超出可分配设备数 → 多余 SN 记为无匹配设备
            if (i >= matchCount) {
                skipped.add(new SkipEntry(sn, null, "无匹配设备"));
                continue;
            }

            DeviceAsset device = devices.get(i);
            try {
                if (sn.startsWith(RESERVED_PREFIX)) {
                    skipped.add(new SkipEntry(sn, device.getId(), "SN 保留字前缀不允许"));
                    continue;
                }

                // 并发安全：重新加载最新状态
                DeviceAsset fresh = deviceAssetRepo.findById(device.getId()).orElse(null);
                if (fresh == null) {
                    skipped.add(new SkipEntry(sn, device.getId(), "设备资产不存在"));
                    continue;
                }
                if (fresh.getSn() != null) {
                    skipped.add(new SkipEntry(sn, fresh.getId(), "设备已绑定 SN"));
                    continue;
                }
                if (fresh.getStage() != DeviceAsset.Stage.PENDING_MATCH) {
                    skipped.add(new SkipEntry(sn, fresh.getId(), "设备状态不允许绑定 SN"));
                    continue;
                }
                if (deviceAssetRepo.existsBySn(sn)) {
                    skipped.add(new SkipEntry(sn, fresh.getId(), "SN 已被使用"));
                    continue;
                }

                fresh.setSn(sn);
                deviceAssetRepo.save(fresh);
                auditLog.log(adminId, "BATCH_BIND_SN", "device_asset",
                        String.valueOf(fresh.getId()),
                        "{\"sn\":\"" + sn + "\",\"batch_index\":" + i + "}");
                bound++;
            } catch (Exception e) {
                skipped.add(new SkipEntry(sn, device.getId(), "绑定异常: " + e.getMessage()));
            }
        }

        return new BatchBindResult(total, bound, skipped);
    }

    /** 解析目标设备：指定模式按 id 顺序查，自动模式 FIFO 取未绑 PENDING_MATCH。 */
    private List<DeviceAsset> resolveDevices(List<Long> deviceAssetIds, int snCount) {
        if (deviceAssetIds != null && !deviceAssetIds.isEmpty()) {
            List<DeviceAsset> devices = new ArrayList<>();
            for (Long id : deviceAssetIds) {
                deviceAssetRepo.findById(id).ifPresent(devices::add);
            }
            return devices;
        }
        return deviceAssetRepo.findUnboundByStageOrderByIdAsc(DeviceAsset.Stage.PENDING_MATCH, snCount);
    }

    /** 清洗 + 去重（trim、去空、保留首次出现顺序）。 */
    private List<String> sanitizeSns(List<String> raw) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String s : raw) {
            if (s == null) continue;
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) set.add(trimmed);
        }
        return new ArrayList<>(set);
    }

    public record BatchBindResult(
            int total,
            int bound,
            List<SkipEntry> skipped
    ) {}

    public record SkipEntry(String sn, Long deviceAssetId, String reason) {}
}
