package com.sanshuiyuan.ess.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 签署流程记录实体。
 */
@Entity
@Table(name = "ess_flow_records")
public class EssFlowRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_id", nullable = false, unique = true, length = 64)
    private String contractId;

    @Column(name = "ess_flow_id", length = 128)
    private String essFlowId;

    @Enumerated(EnumType.STRING)
    @Column(name = "flow_status", nullable = false, length = 32)
    private FlowStatus flowStatus;

    @Column(name = "signer_list_json", columnDefinition = "TEXT")
    private String signerListJson;

    @Column(name = "callback_data_json", columnDefinition = "TEXT")
    private String callbackDataJson;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected EssFlowRecord() {
    }

    /**
     * 创建签署流程记录。
     */
    public static EssFlowRecord create(String contractId, String signerListJson) {
        EssFlowRecord record = new EssFlowRecord();
        record.contractId = contractId;
        record.flowStatus = FlowStatus.INIT;
        record.signerListJson = signerListJson;
        return record;
    }

    public void assignFlowId(String essFlowId) {
        this.essFlowId = essFlowId;
        this.flowStatus = FlowStatus.CREATED;
    }

    public void startSigning() {
        this.flowStatus = FlowStatus.SIGNING;
    }

    public void complete(String callbackDataJson) {
        this.flowStatus = FlowStatus.COMPLETED;
        this.callbackDataJson = callbackDataJson;
    }

    public void cancel() {
        this.flowStatus = FlowStatus.CANCELLED;
    }

    public void reject(String callbackDataJson) {
        this.flowStatus = FlowStatus.REJECTED;
        this.callbackDataJson = callbackDataJson;
    }

    public void markError(String callbackDataJson) {
        this.flowStatus = FlowStatus.ERROR;
        this.callbackDataJson = callbackDataJson;
    }

    public void updateCallbackData(String callbackDataJson) {
        this.callbackDataJson = callbackDataJson;
    }

    public Long getId() { return id; }
    public String getContractId() { return contractId; }
    public String getEssFlowId() { return essFlowId; }
    public FlowStatus getFlowStatus() { return flowStatus; }
    public String getSignerListJson() { return signerListJson; }
    public String getCallbackDataJson() { return callbackDataJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
