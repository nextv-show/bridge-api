package com.sanshuiyuan.ess.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 合同模板实体。
 * <p>
 * 管理主合同模板与附件模板，支持版本管理。
 */
@Entity
@Table(name = "contract_templates")
public class ContractTemplate {

    /**
     * 模板类型枚举。
     */
    public enum TemplateType {
        /** 主合同 */
        MAIN,
        /** 附件 */
        ATTACHMENT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_code", nullable = false, length = 64)
    private String templateCode;

    @Column(name = "template_name", nullable = false, length = 256)
    private String templateName;

    @Enumerated(EnumType.STRING)
    @Column(name = "template_type", nullable = false, length = 32)
    private TemplateType templateType;

    @Column(name = "content_body", nullable = false, columnDefinition = "TEXT")
    private String contentBody;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected ContractTemplate() {
    }

    /**
     * 创建新的合同模板。
     */
    public static ContractTemplate create(String templateCode, String templateName,
                                           TemplateType templateType, String contentBody, int version) {
        ContractTemplate t = new ContractTemplate();
        t.templateCode = templateCode;
        t.templateName = templateName;
        t.templateType = templateType;
        t.contentBody = contentBody;
        t.version = version;
        return t;
    }

    /**
     * 创建新版本模板。
     */
    public ContractTemplate newVersion(String contentBody) {
        return ContractTemplate.create(this.templateCode, this.templateName,
                this.templateType, contentBody, this.version + 1);
    }

    public Long getId() { return id; }
    public String getTemplateCode() { return templateCode; }
    public String getTemplateName() { return templateName; }
    public TemplateType getTemplateType() { return templateType; }
    public String getContentBody() { return contentBody; }
    public int getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
