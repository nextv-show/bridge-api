package com.sanshuiyuan.ess.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 合同访问审计日志实体。
 * <p>
 * 记录所有对合同 PDF 的访问行为（查看/下载），支持 H5 端和管理后台。
 */
@Entity
@Table(name = "contract_access_logs")
public class ContractAccessLog {

    /**
     * 访问类型枚举。
     */
    public enum AccessType {
        /** H5 查看 */
        VIEW,
        /** H5 下载 */
        DOWNLOAD,
        /** 管理后台查看 */
        ADMIN_VIEW,
        /** 管理后台下载 */
        ADMIN_DOWNLOAD
    }

    /**
     * 访问来源枚举。
     */
    public enum AccessSource {
        /** H5 移动端 */
        H5,
        /** 管理后台 */
        ADMIN,
        /** API 调用 */
        API
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_type", nullable = false, length = 32)
    private AccessType accessType;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_source", nullable = false, length = 32)
    private AccessSource accessSource;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ContractAccessLog() {
    }

    /**
     * 创建访问日志。
     */
    public static ContractAccessLog create(Long contractId, Long userId,
                                            AccessType accessType, AccessSource accessSource,
                                            String ipAddress, String userAgent) {
        ContractAccessLog log = new ContractAccessLog();
        log.contractId = contractId;
        log.userId = userId;
        log.accessType = accessType;
        log.accessSource = accessSource;
        log.ipAddress = ipAddress;
        log.userAgent = userAgent;
        return log;
    }

    public Long getId() { return id; }
    public Long getContractId() { return contractId; }
    public Long getUserId() { return userId; }
    public AccessType getAccessType() { return accessType; }
    public AccessSource getAccessSource() { return accessSource; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
