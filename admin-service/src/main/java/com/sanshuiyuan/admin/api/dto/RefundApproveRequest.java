package com.sanshuiyuan.admin.api.dto;

/**
 * 退款审批通过请求 — 可选调整实退金额（cents）
 */
public class RefundApproveRequest {
    private Long actualRefundCents;

    public Long getActualRefundCents() { return actualRefundCents; }
    public void setActualRefundCents(Long actualRefundCents) { this.actualRefundCents = actualRefundCents; }
}
