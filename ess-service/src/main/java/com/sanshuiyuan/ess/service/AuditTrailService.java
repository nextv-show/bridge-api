package com.sanshuiyuan.ess.service;

import com.sanshuiyuan.ess.domain.ContractAuditTrail;
import com.sanshuiyuan.ess.domain.ContractAuditTrail.Action;
import com.sanshuiyuan.ess.domain.ContractAuditTrail.ActorType;
import com.sanshuiyuan.ess.infra.repository.ContractAuditTrailRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 审计轨迹服务。
 * <p>
 * 记录合同全生命周期的所有操作（创建/签署/归档/出证/撤销/查看/下载），
 * 提供审计轨迹查询能力。
 */
@Service
public class AuditTrailService {

    private static final Logger log = LoggerFactory.getLogger(AuditTrailService.class);

    private final ContractAuditTrailRepository auditTrailRepository;

    public AuditTrailService(ContractAuditTrailRepository auditTrailRepository) {
        this.auditTrailRepository = auditTrailRepository;
    }

    /**
     * 记录审计事件。
     *
     * @param contractId   合同 ID
     * @param action       操作类型
     * @param actorId      操作者 ID
     * @param actorType    操作者类型
     * @param metadataJson 附加元数据
     * @param ipAddress    IP 地址
     * @return 审计记录
     */
    @Transactional
    public ContractAuditTrail recordEvent(Long contractId, Action action,
                                           String actorId, ActorType actorType,
                                           String metadataJson, String ipAddress) {
        log.debug("记录审计事件 [contractId={}, action={}, actorId={}, actorType={}]",
                contractId, action, actorId, actorType);

        ContractAuditTrail trail = ContractAuditTrail.create(
                contractId, action, actorId, actorType, metadataJson, ipAddress);
        return auditTrailRepository.save(trail);
    }

    /**
     * 记录系统操作审计事件（便捷方法）。
     */
    @Transactional
    public ContractAuditTrail recordSystemEvent(Long contractId, Action action,
                                                 String metadataJson) {
        return recordEvent(contractId, action, null, ActorType.SYSTEM, metadataJson, null);
    }

    /**
     * 记录用户操作审计事件（便捷方法）。
     */
    @Transactional
    public ContractAuditTrail recordUserEvent(Long contractId, Action action,
                                               Long userId, String metadataJson,
                                               String ipAddress) {
        return recordEvent(contractId, action, String.valueOf(userId),
                ActorType.USER, metadataJson, ipAddress);
    }

    /**
     * 记录管理员操作审计事件（便捷方法）。
     */
    @Transactional
    public ContractAuditTrail recordAdminEvent(Long contractId, Action action,
                                                Long adminId, String metadataJson,
                                                String ipAddress) {
        return recordEvent(contractId, action, String.valueOf(adminId),
                ActorType.ADMIN, metadataJson, ipAddress);
    }

    /**
     * 查询合同的审计轨迹（分页）。
     *
     * @param contractId 合同 ID
     * @param pageable   分页参数
     * @return 审计轨迹分页
     */
    @Transactional(readOnly = true)
    public Page<ContractAuditTrail> getAuditTrail(Long contractId, Pageable pageable) {
        return auditTrailRepository.findByContractId(contractId, pageable);
    }

    /**
     * 查询合同的完整审计轨迹（全量，按时间倒序）。
     *
     * @param contractId 合同 ID
     * @return 审计轨迹列表
     */
    @Transactional(readOnly = true)
    public List<ContractAuditTrail> getFullAuditTrail(Long contractId) {
        return auditTrailRepository.findByContractIdOrderByCreatedAtDesc(contractId);
    }

    /**
     * 统计合同某类操作的次数。
     */
    @Transactional(readOnly = true)
    public long countByContractIdAndAction(Long contractId, Action action) {
        return auditTrailRepository.countByContractIdAndAction(contractId, action);
    }
}
