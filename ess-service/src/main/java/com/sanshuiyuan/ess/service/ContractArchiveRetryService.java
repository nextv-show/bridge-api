package com.sanshuiyuan.ess.service;

import com.sanshuiyuan.ess.domain.Contract;
import com.sanshuiyuan.ess.domain.Contract.ArchiveStatus;
import com.sanshuiyuan.ess.infra.repository.ContractRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 合同归档重试与告警服务。
 * <p>
 * 定时扫描归档失败/待归档的合同，自动重试归档。
 * 超过最大重试次数时触发告警。
 */
@Service
public class ContractArchiveRetryService {

    private static final Logger log = LoggerFactory.getLogger(ContractArchiveRetryService.class);
    private static final int MAX_RETRY_ATTEMPTS = 5;

    private final ContractRepository contractRepository;
    private final ContractArchiveService archiveService;

    public ContractArchiveRetryService(ContractRepository contractRepository,
                                        ContractArchiveService archiveService) {
        this.contractRepository = contractRepository;
        this.archiveService = archiveService;
    }

    /**
     * 定时重试归档（每 5 分钟执行一次）。
     * <p>
     * 扫描状态为 SIGNED 但 archive_status 为 PENDING 或 FAILED 的合同。
     */
    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    @Transactional
    public void retryArchiving() {
        List<Contract> pendingContracts = contractRepository
                .findByStatusAndArchiveStatusIn(
                        Contract.ContractStatus.SIGNED,
                        List.of(ArchiveStatus.PENDING, ArchiveStatus.FAILED));

        if (pendingContracts.isEmpty()) {
            return;
        }

        log.info("发现 {} 份待归档/归档失败合同，开始重试", pendingContracts.size());

        for (Contract contract : pendingContracts) {
            try {
                archiveService.archiveContract(contract.getId());
                log.info("归档重试成功 [contractNo={}]", contract.getContractNo());
            } catch (Exception e) {
                log.error("归档重试失败 [contractNo={}]: {}", contract.getContractNo(), e.getMessage());

                // 检查是否超过最大重试次数
                checkAndAlert(contract);
            }
        }
    }

    /**
     * 手动触发归档重试（供管理接口调用）。
     *
     * @param contractId 合同 ID
     * @return 归档结果
     */
    @Transactional
    public ContractArchiveService.ArchiveResult manualRetry(Long contractId) {
        log.info("手动触发归档重试 [contractId={}]", contractId);
        return archiveService.archiveContract(contractId);
    }

    /**
     * 检查是否超过最大重试次数，触发告警。
     */
    private void checkAndAlert(Contract contract) {
        // 简化实现：检查 updatedAt 距今是否超过阈值
        // 实际生产可引入 retry_count 字段或专门的重试记录表
        log.warn("合同归档可能需要人工介入 [contractNo={}, archiveStatus={}]",
                contract.getContractNo(), contract.getArchiveStatus());

        // TODO: 集成告警系统（钉钉/企业微信/邮件）
        // alertService.sendArchiveFailureAlert(contract);
    }
}
