package com.sanshuiyuan.ess.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 协议同意记录实体。
 * <p>
 * 记录用户对协议类模板（{@link ContractTemplate.TemplateType#AGREEMENT}）的同意行为，
 * 包含同意时的模板版本、客户端类型与来源 IP，用于合规留痕与争议处理。
 */
@Entity
@Table(name = "agreement_acceptances")
public class AgreementAcceptance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "openid", nullable = false, length = 128)
    private String openid;

    @Column(name = "agreement_code", nullable = false, length = 64)
    private String agreementCode;

    @Column(name = "template_version", nullable = false)
    private int templateVersion;

    @Column(name = "accepted_at", insertable = false, updatable = false)
    private LocalDateTime acceptedAt;

    @Column(name = "client_type", nullable = false, length = 16)
    private String clientType;

    @Column(name = "client_ip", length = 45)
    private String clientIp;

    protected AgreementAcceptance() {
    }

    /**
     * 创建新的协议同意记录。
     */
    public static AgreementAcceptance create(String openid, String agreementCode,
                                             int templateVersion, String clientType, String clientIp) {
        AgreementAcceptance a = new AgreementAcceptance();
        a.openid = openid;
        a.agreementCode = agreementCode;
        a.templateVersion = templateVersion;
        a.clientType = clientType;
        a.clientIp = clientIp;
        return a;
    }

    public Long getId() { return id; }
    public String getOpenid() { return openid; }
    public String getAgreementCode() { return agreementCode; }
    public int getTemplateVersion() { return templateVersion; }
    public LocalDateTime getAcceptedAt() { return acceptedAt; }
    public String getClientType() { return clientType; }
    public String getClientIp() { return clientIp; }
}
