package com.sanshuiyuan.matching.request.infra;

import com.sanshuiyuan.matching.request.domain.DeviceStage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 受限设备资产网关：只允许读取/修改 device_assets 的必要字段，避免 matching-service 直接接触资产域 JPA。
 */
@Component
public class DeviceAssetGateway {

    /**
     * 唯一允许写 device_assets 的语句：只推进 stage，且 WHERE 同时校验归属(user_id)与前置态(stage)，
     * 使推进具 CAS 语义（命中 0 行即归属或前置态不符，调用方据此判 403/409）。
     */
    public static final String UPDATE_STAGE_SQL =
            "UPDATE device_assets SET stage = ? WHERE id = ? AND user_id = ? AND stage = ?";

    private final JdbcTemplate jdbcTemplate;

    public DeviceAssetGateway(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean existsOwnedByUser(long deviceAssetId, long ownerUserId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM device_assets WHERE id = ? AND user_id = ?",
                Integer.class,
                deviceAssetId,
                ownerUserId
        );
        return count != null && count > 0;
    }

    public long countLockedByOwner(long ownerUserId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM device_assets WHERE user_id = ? AND stage = ?",
                Long.class,
                ownerUserId,
                DeviceStage.LOCKED.name()
        );
        return count == null ? 0L : count;
    }

    /**
     * CAS 推进设备阶段：仅当资产归属 {@code ownerUserId} 且当前为 {@code expected} 时改为 {@code next}。
     * 返回受影响行数（1=成功；0=归属或前置态不符，调用方判 403/409）。
     */
    public int advanceStage(long deviceAssetId, long ownerUserId, DeviceStage expected, DeviceStage next) {
        return jdbcTemplate.update(
                UPDATE_STAGE_SQL,
                next.name(),
                deviceAssetId,
                ownerUserId,
                expected.name()
        );
    }

    public List<Long> findLockedAssetIdsByOwner(long ownerUserId) {
        return jdbcTemplate.queryForList(
                "SELECT id FROM device_assets WHERE user_id = ? AND stage = ? ORDER BY id ASC",
                Long.class,
                ownerUserId,
                DeviceStage.LOCKED.name()
        );
    }
}
