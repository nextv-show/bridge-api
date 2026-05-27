package com.sanshuiyuan.ess.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 合同实例实体。
 * <p>
 * 记录每份合同的完整生命周期：DRAFT → GENERATED → SIGNING → SIGNED → ARCHIVED。
 */
@Entity
@Table(name = "contracts")
public class Contract {

    /**
     * 归档状态枚举。
     */
    public enum ArchiveStatus {
        /** 待归档 */
        PENDING,
        /** 归档中 */
        ARCHIVING,
        /** 已归档 */
        ARCHIVED,
        /** 归档失败 */
        FAILED;
    }

    /**
     * 合同状态枚举（状态机）。
     */
    public enum ContractStatus {
        /** 草稿（刚创建，尚未填充模板） */
        DRAFT,
        /** 已生成（模板已填充） */
        GENERATED,
        /** 签署中 */
        SIGNING,
        /** 已签署 */
        SIGNED,
        /** 已归档 */
        ARCHIVED;

        /**
         * 校验状态流转是否合法。
         */
        public boolean canTransitionTo(ContractStatus target) {
            return switch (this) {
                case DRAFT -> target == GENERATED;
                case GENERATED -> target == SIGNING;
                case SIGNING -> target == SIGNED;
                case SIGNED -> target == ARCHIVED;
                case ARCHIVED -> false;
            };
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_no", nullable = false, unique = true, length = 32)
    private String contractNo;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_id", length = 64)
    private String orderId;

    @Column(name = "device_sn", length = 64)
    private String deviceSn;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ContractStatus status;

    @Column(name = "signer_info_json", columnDefinition = "TEXT")
    private String signerInfoJson;

    @Column(name = "contract_fields_json", columnDefinition = "TEXT")
    private String contractFieldsJson;

    @Column(name = "pdf_url", length = 512)
    private String pdfUrl;

    @Column(name = "pdf_hash", length = 128)
    private String pdfHash;

    @Column(name = "tencent_cloud_url", length = 512)
    private String tencentCloudUrl;

    @Column(name = "oss_url", length = 512)
    private String ossUrl;

    @Column(name = "archive_status", length = 32)
    private ArchiveStatus archiveStatus;

    @Column(name = "certificate_no", length = 128)
    private String certificateNo;

    @Column(name = "download_count")
    private int downloadCount;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @Column(name = "ess_flow_id", length = 128)
    private String essFlowId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected Contract() {
    }

    /**
     * 创建合同草稿。
     */
    public static Contract createDraft(String contractNo, Long templateId, Long userId,
                                        String orderId, String deviceSn) {
        Contract c = new Contract();
        c.contractNo = contractNo;
        c.templateId = templateId;
        c.userId = userId;
        c.orderId = orderId;
        c.deviceSn = deviceSn;
        c.status = ContractStatus.DRAFT;
        return c;
    }

    /**
     * 生成合同（模板填充完毕）。
     */
    public void markGenerated(String contractFieldsJson, String signerInfoJson) {
        validateTransition(ContractStatus.GENERATED);
        this.status = ContractStatus.GENERATED;
        this.contractFieldsJson = contractFieldsJson;
        this.signerInfoJson = signerInfoJson;
    }

    /**
     * 进入签署流程。
     */
    public void startSigning(String essFlowId) {
        validateTransition(ContractStatus.SIGNING);
        this.status = ContractStatus.SIGNING;
        this.essFlowId = essFlowId;
    }

    /**
     * 签署完成。
     */
    public void completeSigning(String pdfUrl, String pdfHash) {
        validateTransition(ContractStatus.SIGNED);
        this.status = ContractStatus.SIGNED;
        this.pdfUrl = pdfUrl;
        this.pdfHash = pdfHash;
    }

    /**
     * 归档。
     */
    public void archive() {
        validateTransition(ContractStatus.ARCHIVED);
        this.status = ContractStatus.ARCHIVED;
        this.archiveStatus = ArchiveStatus.ARCHIVED;
        this.archivedAt = LocalDateTime.now();
    }

    /**
     * 标记为待归档。
     */
    public void markPendingArchive() {
        this.archiveStatus = ArchiveStatus.PENDING;
    }

    /**
     * 标记为归档中。
     */
    public void markArchiving() {
        this.archiveStatus = ArchiveStatus.ARCHIVING;
    }

    /**
     * 标记归档失败。
     */
    public void markArchiveFailed() {
        this.archiveStatus = ArchiveStatus.FAILED;
    }

    /**
     * 更新归档存储信息。
     */
    public void updateArchiveUrls(String tencentCloudUrl, String ossUrl, String pdfHash) {
        this.tencentCloudUrl = tencentCloudUrl;
        this.ossUrl = ossUrl;
        this.pdfHash = pdfHash;
    }

    /**
     * 增加下载次数。
     */
    public void incrementDownloadCount() {
        this.downloadCount++;
    }

    private void validateTransition(ContractStatus target) {
        if (!this.status.canTransitionTo(target)) {
            throw new IllegalStateException(
                    String.format("合同状态不允许从 %s 转换到 %s [contractNo=%s]",
                            this.status, target, this.contractNo));
        }
    }

    public Long getId() { return id; }
    public String getContractNo() { return contractNo; }
    public Long getTemplateId() { return templateId; }
    public Long getUserId() { return userId; }
    public String getOrderId() { return orderId; }
    public String getDeviceSn() { return deviceSn; }
    public ContractStatus getStatus() { return status; }
    public String getSignerInfoJson() { return signerInfoJson; }
    public String getContractFieldsJson() { return contractFieldsJson; }
    public String getPdfUrl() { return pdfUrl; }
    public String getPdfHash() { return pdfHash; }
    public String getTencentCloudUrl() { return tencentCloudUrl; }
    public String getOssUrl() { return ossUrl; }
    public ArchiveStatus getArchiveStatus() { return archiveStatus; }
    public String getCertificateNo() { return certificateNo; }
    public int getDownloadCount() { return downloadCount; }
    public LocalDateTime getArchivedAt() { return archivedAt; }
    public String getEssFlowId() { return essFlowId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
