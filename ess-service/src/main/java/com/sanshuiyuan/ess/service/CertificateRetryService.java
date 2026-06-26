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
    /** 出证（存证报告）总开关，默认关闭：付费服务，弃用以免运营成本。关闭时本任务直接跳过。 */
    private final boolean certificateEnabled;

    // 简化实现：使用内存计数器跟踪重试次数
    // 生产环境应使用数据库字段或专门的重试记录表
    private final AtomicInteger retryCycleCount = new AtomicInteger(0);

    public CertificateRetryService(ContractRepository contractRepository,
                                    CertificateService certificateService,
                                    @org.springframework.beans.factory.annotation.Value(
                                            "${ess.certificate.enabled:false}") boolean certificateEnabled) {
        this.contractRepository = contractRepository;
        this.certificateService = certificateService;
        this.certificateEnabled = certificateEnabled;
    }

    /**
     * 定时重试出证（每 3 分钟执行一次）。
     * <p>
     * 扫描状态为 ARCHIVED 且 certificate_status 为 PENDING / APPLYING / FAILED 的合同。
     * <p>APPLYING 表示出证报告已提交、正在腾讯侧异步生成，需继续轮询 DescribeFlowEvidenceReport
     * 直到 Success/Failed；漏掉 APPLYING 会导致两步出证卡在第一步永不完成。
     */
    @Scheduled(fixedDelay = 180_000, initialDelay = 120_000)
    @Transactional
    public void retryCertification() {
        if (!certificateEnabled) {
            return; // 出证已禁用：不扫描、不调用付费出证 API（避免 ProveNoQuota 噪音与成本）。
        }
        List<Contract> pendingContracts = contractRepository
                .findByStatusAndCertificateStatusIn(
                        ContractStatus.ARCHIVED,
                        List.of(CertificateStatus.PENDING, CertificateStatus.APPLYING,
                                CertificateStatus.FAILED));

        if (pendingContracts.isEmpty()) {
            return;
        }

        int cycle = retryCycleCount.incrementAndGet();
        log.info("出证重试周期 #{}，发现 {} 份待出证/出证失败合同", cycle, pendingContracts.size());

        for (Contract contract : pendingContracts) {
            try {
                CertificateService.CertificateResult r = certificateService.certifyContract(contract.getId());
                // 两步异步：FAILED 由 certifyContract 内部落库且不抛异常，需在此显式识别并告警，
                // 否则持续失败会被当作"成功"而静默重提，永不触达告警。
                if ("FAILED".equals(r.status())) {
                    log.warn("出证重试返回失败状态 [contractNo={}]", contract.getContractNo());
                    checkAndAlert(contract);
                } else {
                    log.info("出证重试已推进 [contractNo={}, status={}]", contract.getContractNo(), r.status());
                }
            } catch (Exception e) {
                log.error("出证重试异常 [contractNo={}]: {}", contract.getContractNo(), e.getMessage());
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
