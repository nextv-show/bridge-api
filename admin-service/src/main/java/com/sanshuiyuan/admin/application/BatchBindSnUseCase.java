package com.sanshuiyuan.admin.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanshuiyuan.admin.domain.DeviceAsset;
import com.sanshuiyuan.admin.infra.repository.DeviceAssetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 批量绑定 SN——支持指定设备模式与自动分配模式。
 *
 * <p>每条绑定走独立 {@code REQUIRES_NEW} 事务，保证 save + audit 原子（其一失败则整条回滚并记入 skipped），
 * 同时实现批量"部分成功"语义。绑定本身用条件 UPDATE（CAS）替代 read-then-write，消除并发竞态。
 * 校验逻辑与 {@link BindSnUseCase} 一致，但改为收集错误而非抛异常。
 */
@Service
public class BatchBindSnUseCase {

    /** 单次批量上限。 */
    private static final int MAX_BATCH = 200;

    /** SN 保留字前缀——系统占位 SN，不允许人工绑定。 */
    private static final String RESERVED_PREFIX = "SN-PENDING-";

    private static final ObjectMapper OM = new ObjectMapper();

    private final DeviceAssetRepository deviceAssetRepo;
    private final AuditLogService auditLog;
    private final PlatformTransactionManager txManager;

    public BatchBindSnUseCase(DeviceAssetRepository deviceAssetRepo, AuditLogService auditLog,
                              PlatformTransactionManager txManager) {
        this.deviceAssetRepo = deviceAssetRepo;
        this.auditLog = auditLog;
        this.txManager = txManager;
    }

    /**
     * 批量绑定 SN。
     *
     * @param adminId        操作人
     * @param sns            SN 列表（trim；空白/批次内重复记入 skipped 而非静默丢弃）
     * @param deviceAssetIds 指定设备 ID 列表；为 null/空时走自动分配模式（FIFO 取 PENDING_MATCH 未绑设备）
     * @return 绑定结果（提交总数 / 成功数 / 跳过列表）
     */
    public BatchBindResult batchBindSn(Long adminId, List<String> sns, List<Long> deviceAssetIds) {
        if (sns == null || sns.isEmpty()) {
            throw new IllegalArgumentException("SN 列表不能为空");
        }
        if (sns.size() > MAX_BATCH) {
            throw new IllegalArgumentException("单次批量上限 " + MAX_BATCH + " 条，当前: " + sns.size());
        }

        int submittedCount = sns.size();
        List<SkipEntry> skipped = new ArrayList<>();

        // 清洗：空白/批次内重复记入 skipped，保留首次出现顺序，便于运维对账。
        List<String> cleanSns = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String raw : sns) {
            if (raw == null) {
                skipped.add(new SkipEntry(null, null, "SN 为空"));
                continue;
            }
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                skipped.add(new SkipEntry(raw, null, "SN 为空"));
                continue;
            }
            if (!seen.add(trimmed)) {
                skipped.add(new SkipEntry(trimmed, null, "SN 在批次内重复"));
                continue;
            }
            cleanSns.add(trimmed);
        }

        // 指定模式下用 null 占位保持 SN↔deviceAssetId 位置对应，避免缺失 ID 压缩列表导致错位。
        List<DeviceAsset> devices = resolveDevices(deviceAssetIds, cleanSns.size());

        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        int bound = 0;
        for (int i = 0; i < cleanSns.size(); i++) {
            String sn = cleanSns.get(i);
            DeviceAsset device = i < devices.size() ? devices.get(i) : null;
            // device 为 null：自动模式 SN 数 > 设备数，或指定模式该位置 ID 不存在。
            Long fallbackId = (deviceAssetIds != null && i < deviceAssetIds.size())
                    ? deviceAssetIds.get(i) : null;

            if (device == null) {
                skipped.add(new SkipEntry(sn, fallbackId, "设备不存在或无匹配设备"));
                continue;
            }

            final int idx = i;
            final Long deviceId = device.getId();
            try {
                Optional<String> skipReason = tx.execute(status -> bindOne(adminId, deviceId, sn, idx));
                if (skipReason == null) {
                    // REQUIRES_NEW + 正常返回理论不会出现 null。
                    skipped.add(new SkipEntry(sn, deviceId, "事务异常"));
                } else if (skipReason.isEmpty()) {
                    bound++;
                } else {
                    skipped.add(new SkipEntry(sn, deviceId, skipReason.get()));
                }
            } catch (Exception e) {
                skipped.add(new SkipEntry(sn, deviceId, "绑定异常: " + e.getMessage()));
            }
        }

        return new BatchBindResult(submittedCount, bound, skipped);
    }

    /**
     * 单条绑定（在调用方 REQUIRES_NEW 事务内执行）——CAS 绑定 + 审计原子提交。
     *
     * @return empty=成功；非空=跳过原因（校验失败走正常返回不触发回滚，异常才回滚）
     */
    private Optional<String> bindOne(Long adminId, Long deviceAssetId, String sn, int batchIndex) {
        if (sn.startsWith(RESERVED_PREFIX)) {
            return Optional.of("SN 保留字前缀不允许");
        }
        if (deviceAssetRepo.existsBySn(sn)) {
            return Optional.of("SN 已被使用");
        }

        // 条件更新：仅当设备未绑 SN 且 PENDING_MATCH 时生效，消除 read-then-write 竞态。
        int affected = deviceAssetRepo.casBindSn(deviceAssetId, sn);
        if (affected == 0) {
            return Optional.of("设备已被绑定或状态不允许");
        }

        auditLog.log(adminId, "BATCH_BIND_SN", "device_asset",
                String.valueOf(deviceAssetId),
                buildAuditPayload(sn, batchIndex));
        return Optional.empty();
    }

    /** 解析目标设备：指定模式按 id 顺序查（不存在用 null 占位保持位置对应），自动模式 FIFO 取未绑 PENDING_MATCH。 */
    private List<DeviceAsset> resolveDevices(List<Long> deviceAssetIds, int snCount) {
        if (deviceAssetIds != null && !deviceAssetIds.isEmpty()) {
            List<DeviceAsset> devices = new ArrayList<>();
            for (Long id : deviceAssetIds) {
                devices.add(deviceAssetRepo.findById(id).orElse(null));
            }
            return devices;
        }
        return deviceAssetRepo.findUnboundByStageOrderByIdAsc(DeviceAsset.Stage.PENDING_MATCH, snCount);
    }

    /** 审计 payload——用 ObjectMapper 序列化，避免 SN 含引号/反斜杠破坏 JSON。 */
    private String buildAuditPayload(String sn, int batchIndex) {
        try {
            var m = new LinkedHashMap<String, Object>();
            m.put("sn", sn);
            m.put("batch_index", batchIndex);
            return OM.writeValueAsString(m);
        } catch (Exception e) {
            return "{\"error\":\"payload serialization failed\"}";
        }
    }

    public record BatchBindResult(
            int total,
            int bound,
            List<SkipEntry> skipped
    ) {}

    public record SkipEntry(String sn, Long deviceAssetId, String reason) {}
}
