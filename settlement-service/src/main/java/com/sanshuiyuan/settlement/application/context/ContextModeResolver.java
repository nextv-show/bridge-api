package com.sanshuiyuan.settlement.application.context;

import com.sanshuiyuan.settlement.domain.DeviceStage;
import com.sanshuiyuan.settlement.infra.asset.DeviceAssetEntity;

import java.util.List;

/**
 * 用户上下文 mode / primaryTask 解析（纯函数，无 IO，便于单测）。
 *
 * 背景与决策（2026-06-18）：小程序首页/个人中心按 userMode 分流。原先 mode 由前端聚合
 * fetchMyAssets + fetchWallet 推导，但 fetchMyAssets 曾读空 asset_db，导致运营中（STAGE_1/STAGE_2）
 * 设备主被误判为 WATER_USER（看不到「设备运营」）。settlement 的 core_db.device_assets 是设备资产真身，
 * 据此在服务端解析 mode 可根治该误判。
 *
 * V1 边界：本解析仅用「设备资产」这一权威本地信号，按运营优先给出
 * WATER_USER / OWNER_PENDING / OWNER_ACTIVE 三态，不做跨服务扇出。
 * MIXED（既运营又用水）需要用水信号，由前端用本地已有的水费/约水数据在收到本结果后做升级，
 * 与前端 services/userContext.ts 的 resolveUserMode/resolvePrimaryTask 口径保持一致。
 */
public final class ContextModeResolver {

    private ContextModeResolver() {}

    /** 设备资产汇总，字段与前端 AssetSummary 对齐。 */
    public record AssetSummary(int total, int pendingMatch, int pendingActivate, int active, int offline) {}

    /** 与 OwnerAssetController 一致：roi 达上限（>=20000bp）且非待撮合视为 fused（已回收/离线表达）。 */
    private static boolean isFused(DeviceAssetEntity a) {
        boolean pendingMatch = a.getStage() == DeviceStage.PENDING_MATCH;
        Integer roiBp = a.getRoiBp();
        return !pendingMatch && roiBp != null && roiBp >= 20000;
    }

    /** 汇总设备资产，口径对齐前端 buildAssetSummary。 */
    public static AssetSummary summarize(List<DeviceAssetEntity> assets) {
        int total = assets.size();
        int pendingMatch = 0, pendingActivate = 0, active = 0, offline = 0;
        for (DeviceAssetEntity a : assets) {
            DeviceStage stage = a.getStage();
            boolean fused = isFused(a);
            if (stage == DeviceStage.PENDING_MATCH) {
                pendingMatch++;
            } else if (stage == DeviceStage.PENDING_ACTIVATE) {
                pendingActivate++;
            } else if (!fused) {
                active++;
            } else {
                offline++;
            }
        }
        return new AssetSummary(total, pendingMatch, pendingActivate, active, offline);
    }

    /**
     * 解析 userMode（运营优先；MIXED 留给前端用水信号升级）。
     * 口径对齐前端 resolveUserMode 在「无用水信号」时的结果：有运营中设备→OWNER_ACTIVE，
     * 仅待撮合→OWNER_PENDING，否则→WATER_USER。
     */
    public static String resolveMode(AssetSummary s) {
        if (s.active() > 0) return "OWNER_ACTIVE";
        if (s.pendingMatch() > 0) return "OWNER_PENDING";
        return "WATER_USER";
    }

    /** 解析首页主任务，口径对齐前端 resolvePrimaryTask。 */
    public static String resolvePrimaryTask(String mode) {
        return switch (mode) {
            case "OWNER_ACTIVE" -> "OPERATE_DEVICE";
            case "OWNER_PENDING" -> "MATCH_DEVICE";
            case "WATER_USER" -> "APPLY_WATER";
            default -> "NONE";
        };
    }
}
