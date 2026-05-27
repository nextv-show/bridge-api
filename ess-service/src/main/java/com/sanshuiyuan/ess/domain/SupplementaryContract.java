package com.sanshuiyuan.ess.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 补充协议实体。
 * <p>
 * 冷静期后退款须签署《设备退货与服务终止补充协议》。
 * 状态流转：DRAFT → GENERATED → SIGNING → SIGNED → ARCHIVED。
 */
@Entity
@Table(name = "supplementary_contracts")
public class SupplementaryContract {

    /**
     * 补充协议类型。
     */
    public enum ContractType {
        /** 设备退货 */
        DEVICE_RETURN,
        /** 服务终止 */
        SERVICE_TERMINATION
    }

    /**
     * 补充协议状态。
     */
    public enum SupplementaryStatus {
        /** 草稿 */
        DRAFT,
        /** 已生成 */
        GENERATED,
        /** 签署中 */
        SIGNING,
        /** 已签署 */
        SIGNED,
        /** 已归档 */
        ARCHIVED;

        public boolean canTransitionTo(SupplementaryStatus target) {
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

    @Column(name = "original_contract_id", nullable = false)
    private Long originalContractId;

    @Column(name = "contract_no", nullable = false, length = 32)
    private String contractNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "contract_type", nullable = false, length = 64)
    private ContractType contractType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private SupplementaryStatus status;

    @Column(name = "pdf_url", length = 512)
    private String pdfUrl;

    @Column(name = "pdf_hash", length = 128)
    private String pdfHash;

    @Column(name = "ess_flow_id", length = 128)
    private String essFlowId;

    @Column(name = "refund_order_id", length = 64)
    private String refundOrderId;

    @Column(name = "signer_info_json", columnDefinition = "TEXT")
    private String signerInfoJson;

    @Column(name = "contract_fields_json", columnDefinition = "TEXT")
    private String contractFieldsJson;

    @Column(name = "signed_at")
    private LocalDateTime signedAt;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected SupplementaryContract() {
    }

    /**
     * 创建补充协议草稿。
     */
    public static SupplementaryContract createDraft(Long originalContractId, String contractNo,
                                                     ContractType contractType, String refundOrderId,
                                                     String signerInfoJson, String contractFieldsJson) {
        SupplementaryContract sc = new SupplementaryContract();
        sc.originalContractId = originalContractId;
        sc.contractNo = contractNo;
        sc.contractType = contractType;
        sc.status = SupplementaryStatus.DRAFT;
        sc.refundOrderId = refundOrderId;
        sc.signerInfoJson = signerInfoJson;
        sc.contractFieldsJson = contractFieldsJson;
        return sc;
    }

    /**
     * 标记为已生成。
     */
    public void markGenerated() {
        validateTransition(SupplementaryStatus.GENERATED);
        this.status = SupplementaryStatus.GENERATED;
    }

    /**
     * 开始签署。
     */
    public void startSigning(String essFlowId) {
        validateTransition(SupplementaryStatus.SIGNING);
        this.status = SupplementaryStatus.SIGNING;
        this.essFlowId = essFlowId;
    }

    /**
     * 签署完成。
     */
    public void completeSigning(String pdfUrl, String pdfHash) {
        validateTransition(SupplementaryStatus.SIGNED);
        this.status = SupplementaryStatus.SIGNED;
        this.pdfUrl = pdfUrl;
        this.pdfHash = pdfHash;
        this.signedAt = LocalDateTime.now();
    }

    /**
     * 归档。
     */
    public void archive() {
        validateTransition(SupplementaryStatus.ARCHIVED);
        this.status = SupplementaryStatus.ARCHIVED;
        this.archivedAt = LocalDateTime.now();
    }

    private void validateTransition(SupplementaryStatus target) {
        if (!this.status.canTransitionTo(target)) {
            throw new IllegalStateException(
                    String.format("补充协议状态不允许从 %s 转换到 %s [contractNo=%s]",
                            this.status, target, this.contractNo));
        }
    }

    public Long getId() { return id; }
    public Long getOriginalContractId() { return originalContractId; }
    public String getContractNo() { return contractNo; }
    public ContractType getContractType() { return contractType; }
    public SupplementaryStatus getStatus() { return status; }
    public String getPdfUrl() { return pdfUrl; }
    public String getPdfHash() { return pdfHash; }
    public String getEssFlowId() { return essFlowId; }
    public String getRefundOrderId() { return refundOrderId; }
    public String getSignerInfoJson() { return signerInfoJson; }
    public String getContractFieldsJson() { return contractFieldsJson; }
    public LocalDateTime getSignedAt() { return signedAt; }
    public LocalDateTime getArchivedAt() { return archivedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
