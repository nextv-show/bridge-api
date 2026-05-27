package com.sanshuiyuan.ess.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 合同审计轨迹实体。
 * <p>
 * 记录合同全生命周期的所有操作：创建、签署、归档、出证、撤销、查看、下载等。
 */
@Entity
@Table(name = "contract_audit_trail")
public class ContractAuditTrail {

    /**
     * 操作类型枚举。
     */
    public enum Action {
        /** 创建合同 */
        CREATE,
        /** 生成合同（模板填充） */
        GENERATE,
        /** 开始签署 */
        START_SIGN,
        /** 签署完成 */
        SIGN_COMPLETE,
        /** 归档 */
        ARCHIVE,
        /** 归档失败 */
        ARCHIVE_FAIL,
        /** 申请出证 */
        CERTIFY,
        /** 出证成功 */
        CERTIFY_SUCCESS,
        /** 出证失败 */
        CERTIFY_FAIL,
        /** 撤销 */
        REVOKE,
        /** 查看 */
        VIEW,
        /** 下载 */
        DOWNLOAD
    }

    /**
     * 操作者类型枚举。
     */
    public enum ActorType {
        /** 用户 */
        USER,
        /** 管理员 */
        ADMIN,
        /** 系统自动 */
        SYSTEM
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 64)
    private Action action;

    @Column(name = "actor_id", length = 64)
    private String actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", length = 32)
    private ActorType actorType;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ContractAuditTrail() {
    }

    /**
     * 创建审计轨迹记录。
     */
    public static ContractAuditTrail create(Long contractId, Action action,
                                             String actorId, ActorType actorType,
                                             String metadataJson, String ipAddress) {
        ContractAuditTrail trail = new ContractAuditTrail();
        trail.contractId = contractId;
        trail.action = action;
        trail.actorId = actorId;
        trail.actorType = actorType;
        trail.metadataJson = metadataJson;
        trail.ipAddress = ipAddress;
        return trail;
    }

    public Long getId() { return id; }
    public Long getContractId() { return contractId; }
    public Action getAction() { return action; }
    public String getActorId() { return actorId; }
    public ActorType getActorType() { return actorType; }
    public String getMetadataJson() { return metadataJson; }
    public String getIpAddress() { return ipAddress; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
