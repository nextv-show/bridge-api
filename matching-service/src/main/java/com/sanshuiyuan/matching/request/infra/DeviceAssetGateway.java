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

    /**
     * SELF_USE 履约推进语句：无匹配需求（无 user 上下文，logistics S2S 调用仅携带设备/物流 ID），
     * 故仅以前置态(stage)做 CAS，归属在 SELF_USE 注册阶段已固定且不可逆。
     */
    public static final String UPDATE_STAGE_BY_DEVICE_SQL =
            "UPDATE device_assets SET stage = ? WHERE id = ? AND stage = ?";

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

    /**
     * SELF_USE 履约 CAS 推进：仅当设备当前为 {@code expected} 时改为 {@code next}，不校验归属。
     * 返回受影响行数（1=成功；0=前置态不符，调用方据当前 stage 判幂等跳过或 409）。
     */
    public int advanceStageByDevice(long deviceAssetId, DeviceStage expected, DeviceStage next) {
        return jdbcTemplate.update(
                UPDATE_STAGE_BY_DEVICE_SQL,
                next.name(),
                deviceAssetId,
                expected.name()
        );
    }

    /** 读取设备当前 stage，设备不存在返回 {@code null}。用于 SELF_USE 履约幂等判定。 */
    public String findStage(long deviceAssetId) {
        List<String> stages = jdbcTemplate.queryForList(
                "SELECT stage FROM device_assets WHERE id = ?",
                String.class,
                deviceAssetId
        );
        return stages.isEmpty() ? null : stages.get(0);
    }

    public List<Long> findLockedAssetIdsByOwner(long ownerUserId) {
        return jdbcTemplate.queryForList(
                "SELECT id FROM device_assets WHERE user_id = ? AND stage = ? ORDER BY id ASC",
                Long.class,
                ownerUserId,
                DeviceStage.LOCKED.name()
        );
    }

    /** 当前用户名下处于 {@code PENDING_MATCH}（可接单）的设备列表，按 id 升序。 */
    public List<PendingMatchDevice> findPendingMatchByOwner(long ownerUserId) {
        return jdbcTemplate.query(
                "SELECT id, sn, stage FROM device_assets WHERE user_id = ? AND stage = ? ORDER BY id ASC",
                (rs, rowNum) -> new PendingMatchDevice(
                        rs.getLong("id"), rs.getString("sn"), rs.getString("stage")),
                ownerUserId,
                DeviceStage.PENDING_MATCH.name()
        );
    }

    /** 受限网关读取投影：device_assets 的 {id, sn, stage} 三列。 */
    public record PendingMatchDevice(long id, String sn, String stage) {}
}
