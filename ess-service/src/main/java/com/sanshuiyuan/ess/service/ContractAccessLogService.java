package com.sanshuiyuan.ess.service;

import com.sanshuiyuan.ess.domain.Contract;
import com.sanshuiyuan.ess.domain.ContractAccessLog;
import com.sanshuiyuan.ess.domain.ContractAccessLog.AccessType;
import com.sanshuiyuan.ess.domain.ContractAccessLog.AccessSource;
import com.sanshuiyuan.ess.infra.repository.ContractAccessLogRepository;
import com.sanshuiyuan.ess.infra.repository.ContractRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 合同访问日志服务。
 * <p>
 * 记录和查询合同 PDF 的所有访问行为（查看/下载），支持审计追踪。
 */
@Service
public class ContractAccessLogService {

    private static final Logger log = LoggerFactory.getLogger(ContractAccessLogService.class);

    private final ContractAccessLogRepository accessLogRepository;
    private final ContractRepository contractRepository;

    public ContractAccessLogService(ContractAccessLogRepository accessLogRepository,
                                     ContractRepository contractRepository) {
        this.accessLogRepository = accessLogRepository;
        this.contractRepository = contractRepository;
    }

    /**
     * 记录访问日志。
     *
     * @param contractId   合同 ID
     * @param userId       用户 ID（可为 null）
     * @param accessType   访问类型
     * @param accessSource 访问来源
     * @param ipAddress    IP 地址
     * @param userAgent    User-Agent
     * @return 访问日志记录
     */
    @Transactional
    public ContractAccessLog logAccess(Long contractId, Long userId,
                                        AccessType accessType, AccessSource accessSource,
                                        String ipAddress, String userAgent) {
        log.debug("记录合同访问 [contractId={}, userId={}, type={}, source={}, ip={}]",
                contractId, userId, accessType, accessSource, ipAddress);

        ContractAccessLog accessLog = ContractAccessLog.create(
                contractId, userId, accessType, accessSource, ipAddress, userAgent);
        return accessLogRepository.save(accessLog);
    }

    /**
     * 查询合同的访问日志（分页）。
     *
     * @param contractId 合同 ID
     * @param pageable   分页参数
     * @return 访问日志分页
     */
    @Transactional(readOnly = true)
    public Page<ContractAccessLog> getAccessLogs(Long contractId, Pageable pageable) {
        return accessLogRepository.findByContractId(contractId, pageable);
    }

    /**
     * 查询合同的访问日志（全量，按时间倒序）。
     *
     * @param contractId 合同 ID
     * @return 访问日志列表
     */
    @Transactional(readOnly = true)
    public List<ContractAccessLog> getAccessLogsByContractId(Long contractId) {
        return accessLogRepository.findByContractIdOrderByCreatedAtDesc(contractId);
    }

    /**
     * 批量查询多份合同的访问日志（分页）。
     *
     * @param contractIds 合同 ID 列表
     * @param pageable    分页参数
     * @return 访问日志分页
     */
    @Transactional(readOnly = true)
    public Page<ContractAccessLog> getAccessLogsByContractIds(List<Long> contractIds, Pageable pageable) {
        return accessLogRepository.findByContractIdIn(contractIds, pageable);
    }

    /**
     * 统计合同某类访问的次数。
     *
     * @param contractId 合同 ID
     * @param accessType 访问类型
     * @return 访问次数
     */
    @Transactional(readOnly = true)
    public long countByContractIdAndAccessType(Long contractId, AccessType accessType) {
        return accessLogRepository.countByContractIdAndAccessType(contractId, accessType);
    }
}
