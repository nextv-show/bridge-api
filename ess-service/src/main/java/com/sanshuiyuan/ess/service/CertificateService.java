package com.sanshuiyuan.ess.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sanshuiyuan.ess.config.EssProperties;
import com.sanshuiyuan.ess.domain.Contract;
import com.sanshuiyuan.ess.domain.Contract.CertificateStatus;
import com.sanshuiyuan.ess.domain.Contract.ContractStatus;
import com.sanshuiyuan.ess.domain.ContractAuditTrail;
import com.sanshuiyuan.ess.infra.client.EssApiClient;
import com.sanshuiyuan.ess.infra.repository.ContractRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.TreeMap;

/**
 * 出证服务（腾讯电子签出证报告）。
 * <p>
 * 出证是<b>两步异步</b>：
 * <ol>
 *   <li>{@code CreateFlowEvidenceReport}(Operator + FlowId + ReportType=0) → 返回 {@code ReportId}，
 *       报告在腾讯侧异步生成（可能需较长时间）；持久化 ReportId，状态置 APPLYING。</li>
 *   <li>{@code DescribeFlowEvidenceReport}(Operator + ReportId) → {@code Status} 为
 *       {@code EvidenceStatusSuccess} 时取 {@code ReportUrl} 完成出证；{@code Executing} 保持 APPLYING
 *       等下一轮；{@code Failed} 清掉 ReportId 置 FAILED 以便重试重提。</li>
 * </ol>
 * 由 {@code CertificateRetryService} 定时驱动两步推进（扫 PENDING/APPLYING/FAILED）。
 * <p>历史 bug：旧实现调用的 {@code CreateCertificate} 在 ESS v2020-11-11 中不存在，恒 InvalidAction。
 */
@Service
public class CertificateService {

    private static final Logger log = LoggerFactory.getLogger(CertificateService.class);

    private final ContractRepository contractRepository;
    private final EssApiClient essApiClient;
    private final EssProperties properties;
    private final AuditTrailService auditTrailService;

    public CertificateService(ContractRepository contractRepository,
                               EssApiClient essApiClient,
                               EssProperties properties,
                               AuditTrailService auditTrailService) {
        this.contractRepository = contractRepository;
        this.essApiClient = essApiClient;
        this.properties = properties;
        this.auditTrailService = auditTrailService;
    }

    /**
     * 对已归档合同申请出证。
     * <p>
     * 调用腾讯电子签出证 API，将出证编号和 PDF URL 绑定到合同。
     *
     * @param contractId 合同 ID
     * @return 出证结果
     */
    @Transactional
    public CertificateResult certifyContract(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("合同不存在: id=" + contractId));

        // 校验前置条件：必须已归档
        if (contract.getStatus() != ContractStatus.ARCHIVED) {
            throw new IllegalStateException(
                    String.format("合同状态为 %s，只有已归档合同才能出证 [contractNo=%s]",
                            contract.getStatus(), contract.getContractNo()));
        }

        // 已出证则跳过
        if (contract.getCertificateStatus() == CertificateStatus.CERTIFIED) {
            log.info("合同已出证，跳过 [contractNo={}]", contract.getContractNo());
            return CertificateResult.alreadyCertified(contractId, contract.getContractNo(),
                    contract.getCertificateNo());
        }

        try {
            if (contract.getEvidenceReportId() == null || contract.getEvidenceReportId().isBlank()) {
                // 步骤1：提交出证报告任务，拿 ReportId（报告异步生成，下一轮再查结果）。
                contract.markCertifying(); // APPLYING
                String reportId = submitEvidenceReport(contract);
                contract.setEvidenceReportId(reportId);
                contractRepository.save(contract);
                log.info("出证报告任务已提交 [contractNo={}, reportId={}]", contract.getContractNo(), reportId);
                return CertificateResult.applying(contractId, contract.getContractNo());
            }
            // 步骤2：查询已提交报告的生成结果。
            return queryEvidenceReport(contract);

        } catch (Exception e) {
            log.error("出证失败 [contractId={}, contractNo={}]: {}",
                    contractId, contract.getContractNo(), e.getMessage(), e);
            contract.markCertificateFailed();
            contractRepository.save(contract);
            try {
                auditTrailService.recordSystemEvent(contractId,
                        ContractAuditTrail.Action.CERTIFY_FAIL,
                        String.format("{\"error\":\"%s\"}", e.getMessage()));
            } catch (Exception auditEx) {
                log.warn("记录出证失败审计事件异常: {}", auditEx.getMessage());
            }
            throw new RuntimeException("出证失败: " + e.getMessage(), e);
        }
    }

    /** 步骤1：CreateFlowEvidenceReport，返回出证报告任务 ReportId。 */
    private String submitEvidenceReport(Contract contract) {
        TreeMap<String, Object> params = new TreeMap<>();
        params.put("Operator", buildOperator());
        params.put("FlowId", contract.getEssFlowId());
        params.put("ReportType", 0); // 0=合同签署报告（默认）
        JsonNode response = essApiClient.invoke("CreateFlowEvidenceReport", params);
        String reportId = response != null && response.has("ReportId")
                ? response.get("ReportId").asText() : null;
        if (reportId == null || reportId.isBlank()) {
            throw new IllegalStateException("CreateFlowEvidenceReport 未返回 ReportId");
        }
        return reportId;
    }

    /** 步骤2：DescribeFlowEvidenceReport，按 Status 推进/保持/失败重提。 */
    private CertificateResult queryEvidenceReport(Contract contract) {
        TreeMap<String, Object> params = new TreeMap<>();
        params.put("Operator", buildOperator());
        params.put("ReportId", contract.getEvidenceReportId());
        JsonNode response = essApiClient.invoke("DescribeFlowEvidenceReport", params);
        String status = response != null && response.has("Status")
                ? response.get("Status").asText() : "";

        switch (status) {
            case "EvidenceStatusSuccess" -> {
                String url = response.has("ReportUrl") ? response.get("ReportUrl").asText() : null;
                contract.completeCertificate(contract.getEvidenceReportId(), url); // CERTIFIED
                contractRepository.save(contract);
                auditTrailService.recordSystemEvent(contract.getId(),
                        ContractAuditTrail.Action.CERTIFY_SUCCESS,
                        String.format("{\"reportId\":\"%s\"}", contract.getEvidenceReportId()));
                log.info("出证报告生成成功 [contractNo={}, reportId={}]",
                        contract.getContractNo(), contract.getEvidenceReportId());
                return CertificateResult.success(contract.getId(), contract.getContractNo(),
                        contract.getEvidenceReportId(), url);
            }
            case "EvidenceStatusFailed" -> {
                // 不抛异常：certifyContract 是 @Transactional，抛出会回滚整笔，导致清理不落库、
                // 重试永远卡在同一个失败 reportId。这里落库清理后正常返回，下轮由 FAILED 扫描
                // （reportId 已空）走步骤1重新提交一个全新出证任务。
                String failedReportId = contract.getEvidenceReportId();
                log.warn("出证报告生成失败，将重新提交 [contractNo={}, reportId={}]",
                        contract.getContractNo(), failedReportId);
                contract.setEvidenceReportId(null);
                contract.markCertificateFailed();
                contractRepository.save(contract);
                auditTrailService.recordSystemEvent(contract.getId(),
                        ContractAuditTrail.Action.CERTIFY_FAIL,
                        String.format("{\"failedReportId\":\"%s\"}", failedReportId));
                return CertificateResult.failed(contract.getId(), contract.getContractNo());
            }
            default -> {
                // EvidenceStatusExecuting（或未知）：仍在生成，保持 APPLYING，等下一轮重试查询。
                log.info("出证报告生成中，待下轮查询 [contractNo={}, reportId={}, status={}]",
                        contract.getContractNo(), contract.getEvidenceReportId(), status);
                return CertificateResult.applying(contract.getId(), contract.getContractNo());
            }
        }
    }

    /**
     * 查询出证状态（向腾讯电子签查询）。
     */
    @Transactional(readOnly = true)
    public CertificateResult queryCertificateStatus(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("合同不存在: id=" + contractId));

        if (contract.getCertificateStatus() == null) {
            return CertificateResult.notApplied(contractId, contract.getContractNo());
        }

        return new CertificateResult(
                contract.getCertificateStatus() == CertificateStatus.CERTIFIED,
                contractId,
                contract.getContractNo(),
                contract.getCertificateNo(),
                contract.getCertificatePdfUrl(),
                contract.getCertificateStatus().name(),
                contract.getCertifiedAt() != null ? contract.getCertifiedAt().toString() : null
        );
    }

    /** SaaS 版 API 经办人入参（与 EssContractService 一致，用 Operator.UserId，非 essbasic 的 Agent）。 */
    private TreeMap<String, Object> buildOperator() {
        TreeMap<String, Object> operator = new TreeMap<>();
        operator.put("UserId", properties.operatorId());
        return operator;
    }

    /**
     * 出证结果。
     */
    public record CertificateResult(
            boolean success,
            Long contractId,
            String contractNo,
            String certificateNo,
            String certificatePdfUrl,
            String status,
            String certifiedAt
    ) {
        public static CertificateResult success(Long contractId, String contractNo,
                                                  String certificateNo, String certificatePdfUrl) {
            return new CertificateResult(true, contractId, contractNo,
                    certificateNo, certificatePdfUrl, "CERTIFIED", null);
        }

        public static CertificateResult alreadyCertified(Long contractId, String contractNo,
                                                           String certificateNo) {
            return new CertificateResult(true, contractId, contractNo,
                    certificateNo, null, "CERTIFIED", null);
        }

        public static CertificateResult notApplied(Long contractId, String contractNo) {
            return new CertificateResult(false, contractId, contractNo,
                    null, null, "NOT_APPLIED", null);
        }

        /** 出证报告已提交但尚在生成中（异步）。 */
        public static CertificateResult applying(Long contractId, String contractNo) {
            return new CertificateResult(false, contractId, contractNo,
                    null, null, "APPLYING", null);
        }

        /** 出证报告生成失败（已清 reportId，下轮重提）。 */
        public static CertificateResult failed(Long contractId, String contractNo) {
            return new CertificateResult(false, contractId, contractNo,
                    null, null, "FAILED", null);
        }
    }
}
