package com.sanshuiyuan.ess.service;

import com.sanshuiyuan.ess.domain.FlowStatus;

/**
 * 腾讯电子签 (ESS) 流程状态 → 内部 {@link FlowStatus} 的统一映射工具。
 * <p>
 * 实际线上响应（{@code DescribeFlowInfo.FlowDetailInfos[].FlowStatus}）使用以下数值语义：
 * <pre>
 *   1  待签署 / 签署中（还有签署方未签署）
 *   2  已签署完成
 *   3  已拒签
 *   4  已撤销
 *   5  已过期
 *   6  已终止
 *   7  以及更高值：流程内部状态（按部分签署/未完成处理，避免误判 COMPLETED）
 * </pre>
 * <p>
 * Webhook 回调里 ESS 也可能直接传字符串枚举（{@code COMPLETED} / {@code FINISH} 等），
 * 一并兼容。任何无法识别的状态返回 {@code null}，由调用方决定是否保留旧状态。
 */
public final class EssStatusMapper {

    private EssStatusMapper() {}

    /**
     * 将 ESS 返回的状态（可能是数字字符串、整型字符串或老的字符串枚举）映射为内部状态。
     *
     * @param essStatus 原始状态字符串；null/空字符串返回 null
     * @return 映射后的内部状态；无法识别返回 null（调用方应保留旧状态而非误判）
     */
    public static FlowStatus map(String essStatus) {
        if (essStatus == null || essStatus.isBlank()) return null;
        return switch (essStatus.trim()) {
            case "1", "SIGNING", "PARTIAL_SIGN" -> FlowStatus.SIGNING;
            case "2", "COMPLETED", "FINISH", "FINISHED", "ALL_SIGN" -> FlowStatus.COMPLETED;
            case "3", "REJECTED", "REJECT" -> FlowStatus.REJECTED;
            case "4", "CANCELLED", "CANCELED", "CANCEL", "WITHDRAW", "REVOKED" -> FlowStatus.CANCELLED;
            case "5", "EXPIRED", "EXPIRE" -> FlowStatus.EXPIRED;
            case "6", "TERMINATED", "TERMINATE", "ABORTED" -> FlowStatus.CANCELLED; // 已终止按取消处理
            // 注意：旧版常见 INIT/CREATED 字面值仅出现在内部初始化路径，远端 ESS 不会返回。
            case "INIT" -> FlowStatus.INIT;
            case "CREATED" -> FlowStatus.CREATED;
            default -> null;
        };
    }

    /**
     * 接受整型形式（来自 {@code asInt()}）的快捷映射。
     */
    public static FlowStatus map(int essStatusCode) {
        return map(Integer.toString(essStatusCode));
    }
}
