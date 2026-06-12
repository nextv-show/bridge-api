package com.sanshuiyuan.ess.service;

import com.sanshuiyuan.ess.domain.FlowStatus;

/**
 * 腾讯电子签 (ESS) 流程状态 → 内部 {@link FlowStatus} 的统一映射工具。
 * <p>
 * 腾讯电子签官方枚举（{@code DescribeFlowInfo.FlowDetailInfos[].FlowStatus}）：
 * <pre>
 *   0  未开启流程（无填写环节）          7  未开启流程（有填写环节）
 *   1  待签署                          8  等待填写
 *   2  部分签署（多方，仍在签署中）       9  部分填写
 *   3  已拒签                          10 已拒填
 *   4  已签署完成  ✅                   16 已失效
 *   5  已过期                          21 已解除
 *   6  已撤销
 * </pre>
 * ⚠️ 历史上本类把 2 误判为 COMPLETED、4 误判为 CANCELLED，导致 C 端签完反被判「已撤销」、合同永不 SIGNED。
 * 现按官方枚举改正：4=COMPLETED、2=SIGNING、6=CANCELLED。
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
            // 1 待签署 / 2 部分签署：都还在签署中（未完成）。
            case "1", "2", "SIGNING", "PARTIAL_SIGN" -> FlowStatus.SIGNING;
            // 4 已签署完成（webhook 字符串枚举一并兼容）。
            case "4", "COMPLETED", "FINISH", "FINISHED", "ALL_SIGN" -> FlowStatus.COMPLETED;
            case "3", "REJECTED", "REJECT" -> FlowStatus.REJECTED;
            // 6 已撤销。
            case "6", "CANCELLED", "CANCELED", "CANCEL", "WITHDRAW", "REVOKED" -> FlowStatus.CANCELLED;
            case "5", "EXPIRED", "EXPIRE" -> FlowStatus.EXPIRED;
            // 16 已失效 / 21 已解除：按取消处理。
            case "16", "21", "TERMINATED", "TERMINATE", "ABORTED" -> FlowStatus.CANCELLED;
            // 注意：旧版常见 INIT/CREATED 字面值仅出现在内部初始化路径，远端 ESS 不会返回。
            case "INIT" -> FlowStatus.INIT;
            case "CREATED" -> FlowStatus.CREATED;
            // 0 / 7 未开启流程、8/9/10 填写环节：保留旧状态，不误判完成。
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
