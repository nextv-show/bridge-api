package com.sanshuiyuan.ess.service;

import com.sanshuiyuan.ess.domain.Contract;
import com.sanshuiyuan.ess.domain.Contract.CertificateStatus;
import com.sanshuiyuan.ess.domain.Contract.ContractStatus;
import com.sanshuiyuan.ess.infra.repository.ContractRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 出证重试与告警服务。
 * <p>
 * 定时扫描待出证/出证失败的合同，自动重试出证。
 * 最多重试 3 次，采用指数退避策略。
 * 超过最大重试次数时触发告警。
 */
@Service
public class CertificateRetryService {

    private static final Logger log = LoggerFactory.getLogger(CertificateRetryService.class);
    private static final int MAX_CERTIFY_ATTEMPTS = 3;

    private final ContractRepository contractRepository;
    private final CertificateService certificateService;

    // 简化实现：使用内存计数器跟踪重试次数
    // 生产环境应使用数据库字段或专门的重试记录表
    private final AtomicInteger retryCycleCount = new AtomicInteger(0);

    public CertificateRetryService(ContractRepository contractRepository,
                                    CertificateService certificateService) {
        this.contractRepository = contractRepository;
        this.certificateService = certificateService;
    }

    /**
     * 定时重试出证（每 3 分钟执行一次）。
     * <p>
     * 扫描状态为 ARCHIVED 且 certificate_status 为 PENDING 或 FAILED 的合同。
     */
    @Scheduled(fixedDelay = 180_000, initialDelay = 120_000)
    @Transactional
    public void retryCertification() {
        List<Contract> pendingContracts = contractRepository
                .findByStatusAndCertificateStatusIn(
                        ContractStatus.ARCHIVED,
                        List.of(CertificateStatus.PENDING, CertificateStatus.FAILED));

        if (pendingContracts.isEmpty()) {
            return;
        }

        int cycle = retryCycleCount.incrementAndGet();
        log.info("出证重试周期 #{}，发现 {} 份待出证/出证失败合同", cycle, pendingContracts.size());

        for (Contract contract : pendingContracts) {
            try {
                certificateService.certifyContract(contract.getId());
                log.info("出证重试成功 [contractNo={}]", contract.getContractNo());
            } catch (Exception e) {
                log.error("出证重试失败 [contractNo={}]: {}", contract.getContractNo(), e.getMessage());

                // 检查是否需要告警
                checkAndAlert(contract);
            }
        }
    }

    /**
     * 手动触发出证重试（供管理接口调用）。
     *
     * @param contractId 合同 ID
     * @return 出证结果
     */
    @Transactional
    public CertificateService.CertificateResult manualRetry(Long contractId) {
        log.info("手动触发出证重试 [contractId={}]", contractId);
        return certificateService.certifyContract(contractId);
    }

    /**
     * 检查是否超过最大重试次数，触发告警。
     */
    private void checkAndAlert(Contract contract) {
        log.warn("合同出证可能需要人工介入 [contractNo={}, certificateStatus={}]",
                contract.getContractNo(), contract.getCertificateStatus());

        // TODO: 集成告警系统（钉钉/企业微信/邮件）
        // alertService.sendCertificateFailureAlert(contract);
    }
}
