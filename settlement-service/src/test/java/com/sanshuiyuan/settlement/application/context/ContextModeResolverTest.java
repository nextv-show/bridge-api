package com.sanshuiyuan.settlement.application.context;

import com.sanshuiyuan.settlement.application.context.ContextModeResolver.AssetSummary;
import com.sanshuiyuan.settlement.domain.DeviceStage;
import com.sanshuiyuan.settlement.infra.asset.DeviceAssetEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContextModeResolverTest {

    // DeviceAssetEntity 构造函数为 protected（JPA），用匿名子类在测试中构造。
    private static DeviceAssetEntity asset(DeviceStage stage, Integer roiBp) {
        DeviceAssetEntity a = new DeviceAssetEntity() {};
        a.setStage(stage);
        a.setRoiBp(roiBp);
        return a;
    }

    @Test
    void emptyAssets_isWaterUser() {
        AssetSummary s = ContextModeResolver.summarize(List.of());
        assertThat(s.total()).isZero();
        assertThat(ContextModeResolver.resolveMode(s)).isEqualTo("WATER_USER");
        assertThat(ContextModeResolver.resolvePrimaryTask("WATER_USER")).isEqualTo("APPLY_WATER");
    }

    @Test
    void onlyPendingMatch_isOwnerPending() {
        // 根因回归：仅有待撮合设备的购机用户，不能落到 WATER_USER。
        AssetSummary s = ContextModeResolver.summarize(List.of(
                asset(DeviceStage.PENDING_MATCH, null)));
        assertThat(s.pendingMatch()).isEqualTo(1);
        assertThat(s.active()).isZero();
        assertThat(ContextModeResolver.resolveMode(s)).isEqualTo("OWNER_PENDING");
        assertThat(ContextModeResolver.resolvePrimaryTask("OWNER_PENDING")).isEqualTo("MATCH_DEVICE");
    }

    @Test
    void stage1Device_isOwnerActive_notWaterUser() {
        // 核心 bug：运营中（STAGE_1）设备主原先被误判为 WATER_USER。
        AssetSummary s = ContextModeResolver.summarize(List.of(
                asset(DeviceStage.STAGE_1, 5000)));
        assertThat(s.active()).isEqualTo(1);
        assertThat(ContextModeResolver.resolveMode(s)).isEqualTo("OWNER_ACTIVE");
        assertThat(ContextModeResolver.resolvePrimaryTask("OWNER_ACTIVE")).isEqualTo("OPERATE_DEVICE");
    }

    @Test
    void activeTakesPriorityOverPendingMatch() {
        // 既有运营中又有待撮合：运营优先 → OWNER_ACTIVE。
        AssetSummary s = ContextModeResolver.summarize(List.of(
                asset(DeviceStage.STAGE_2, 12000),
                asset(DeviceStage.PENDING_MATCH, null)));
        assertThat(s.active()).isEqualTo(1);
        assertThat(s.pendingMatch()).isEqualTo(1);
        assertThat(ContextModeResolver.resolveMode(s)).isEqualTo("OWNER_ACTIVE");
    }

    @Test
    void fusedDevice_countsAsOffline_notActive() {
        // roi 达上限（>=20000bp）视为 fused：计入 offline，不计 active。
        AssetSummary s = ContextModeResolver.summarize(List.of(
                asset(DeviceStage.STAGE_2, 20000)));
        assertThat(s.offline()).isEqualTo(1);
        assertThat(s.active()).isZero();
        // 只有 fused 设备、无其它活跃/待撮合 → 落到 WATER_USER（与前端口径一致）。
        assertThat(ContextModeResolver.resolveMode(s)).isEqualTo("WATER_USER");
    }

    @Test
    void pendingActivate_isCountedSeparately_notActive() {
        AssetSummary s = ContextModeResolver.summarize(List.of(
                asset(DeviceStage.PENDING_ACTIVATE, 0)));
        assertThat(s.pendingActivate()).isEqualTo(1);
        assertThat(s.active()).isZero();
        assertThat(s.pendingMatch()).isZero();
        // 仅待激活、无运营中/待撮合 → WATER_USER（待激活不构成运营视角主任务）。
        assertThat(ContextModeResolver.resolveMode(s)).isEqualTo("WATER_USER");
    }

    @Test
    void summaryCountsAddUp() {
        AssetSummary s = ContextModeResolver.summarize(List.of(
                asset(DeviceStage.PENDING_MATCH, null),
                asset(DeviceStage.PENDING_ACTIVATE, 0),
                asset(DeviceStage.STAGE_1, 3000),
                asset(DeviceStage.STAGE_2, 20000)));
        assertThat(s.total()).isEqualTo(4);
        assertThat(s.pendingMatch() + s.pendingActivate() + s.active() + s.offline()).isEqualTo(s.total());
    }
}
