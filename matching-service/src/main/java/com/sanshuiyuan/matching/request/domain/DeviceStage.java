package com.sanshuiyuan.matching.request.domain;

/**
 * device_assets.stage 状态机（G-1 决议）。写入边界：001 → PENDING_MATCH；002 → LOCKED / PENDING_ACTIVATE；
 * 003 → STAGE_1（首个 MQTT）；004 → STAGE_2。{@code FUSED} 已按 constitution 废除，不在此枚举。
 *
 * <p><b>注意（C.2 阻塞点）</b>：{@link #LOCKED} 为本 Spec 新增的合法转移目标，但 asset-service
 * {@code V003__create_device_assets.sql} 的 {@code stage ENUM} 当前<strong>尚未包含 LOCKED</strong>
 * （仅 PENDING_MATCH/PENDING_ACTIVATE/STAGE_1/STAGE_2/FUSED）。在 C.2 接单事务写 stage=LOCKED 之前，
 * 必须先扩展 device_assets 的 ENUM（device_assets DDL 归 001/asset，不属 V011/V013，本次 C.1 不动）。
 * 本枚举仅作为受限网关 {@link com.sanshuiyuan.matching.request.infra.DeviceAssetGateway} 的类型约束，
 * 不参与 JPA 校验，故此处保留 LOCKED 以表达 G-1 完整状态机。
 */
public enum DeviceStage {
    PENDING_MATCH,
    SELF_USE,
    LOCKED,
    PENDING_ACTIVATE,
    STAGE_1,
    STAGE_2
}
