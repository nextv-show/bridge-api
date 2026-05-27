package com.sanshuiyuan.ess.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 合同与设备 SN 码绑定关系实体。
 * <p>
 * 实现合同 ID 作为 SN 码的唯一法律索引。
 */
@Entity
@Table(name = "contract_sn_bindings")
public class ContractSnBinding {

    /**
     * 绑定类型枚举。
     */
    public enum BindingType {
        /** 预占位（合同生成时自动创建） */
        PRE_ALLOCATED,
        /** 已确认（签署完成后确认） */
        CONFIRMED,
        /** 已释放（合同取消时释放） */
        RELEASED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "device_sn", nullable = false, length = 64)
    private String deviceSn;

    @Enumerated(EnumType.STRING)
    @Column(name = "binding_type", nullable = false, length = 32)
    private BindingType bindingType;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected ContractSnBinding() {
    }

    /**
     * 创建预占位绑定。
     */
    public static ContractSnBinding preAllocate(Long contractId, String deviceSn) {
        ContractSnBinding b = new ContractSnBinding();
        b.contractId = contractId;
        b.deviceSn = deviceSn;
        b.bindingType = BindingType.PRE_ALLOCATED;
        return b;
    }

    /**
     * 确认绑定。
     */
    public void confirm() {
        this.bindingType = BindingType.CONFIRMED;
    }

    /**
     * 释放绑定。
     */
    public void release() {
        this.bindingType = BindingType.RELEASED;
    }

    public Long getId() { return id; }
    public Long getContractId() { return contractId; }
    public String getDeviceSn() { return deviceSn; }
    public BindingType getBindingType() { return bindingType; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
