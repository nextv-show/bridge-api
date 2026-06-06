package com.sanshuiyuan.ess.dto;

/**
 * 协议相关 DTO 集合。
 */
public class AgreementDto {

    private AgreementDto() {
    }

    /** 协议模板摘要（不含 content_body） */
    public record AgreementSummary(String templateCode, String templateName, int version) {}

    /** 协议模板详情（含 content_body） */
    public record AgreementDetail(String templateCode, String templateName, int version, String contentBody) {}

    /** 用户同意请求 */
    public record AcceptRequest(String agreementCode, String clientType) {}

    /** 用户同意记录 */
    public record AcceptanceRecord(String agreementCode, int templateVersion, String acceptedAt, String clientType) {}

    /** 同意状态检查 */
    public record AcceptanceStatus(String agreementCode, boolean accepted) {}
}
