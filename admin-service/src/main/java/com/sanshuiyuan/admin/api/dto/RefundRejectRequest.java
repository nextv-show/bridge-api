package com.sanshuiyuan.admin.api.dto;

/**
 * 退款驳回请求 — 必填驳回原因
 */
public class RefundRejectRequest {
    private String rejectReason;

    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
}
