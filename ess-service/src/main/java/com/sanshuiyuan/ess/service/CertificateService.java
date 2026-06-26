package com.sanshuiyuan.ess.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sanshuiyuan.ess.config.EssProperties;
import com.sanshuiyuan.ess.config.OssProperties;
import com.sanshuiyuan.ess.domain.Contract;
import com.sanshuiyuan.ess.domain.Contract.CertificateStatus;
import com.sanshuiyuan.ess.domain.Contract.ContractStatus;
import com.sanshuiyuan.ess.domain.ContractAuditTrail;
import com.sanshuiyuan.ess.infra.client.EssApiClient;
import com.sanshuiyuan.ess.infra.client.OssStorageClient;
import com.sanshuiyuan.ess.infra.client.TencentCloudStorageClient;
import com.sanshuiyuan.ess.infra.repository.ContractRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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

    private static final String APPLICATION_PDF = "application/pdf";

    private final ContractRepository contractRepository;
    private final EssApiClient essApiClient;
    private final EssProperties properties;
    private final AuditTrailService auditTrailService;
    private final OssStorageClient ossStorageClient;
    private final TencentCloudStorageClient tencentCloudStorageClient;
    private final OssProperties ossProperties;
    /** 出证（存证报告）总开关，默认关闭：付费服务，弃用以免运营成本。 */
    @org.springframework.beans.factory.annotation.Value("${ess.certificate.enabled:false}")
    private boolean certificateEnabled;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    public CertificateService(ContractRepository contractRepository,
                               EssApiClient essApiClient,
                               EssProperties properties,
                               AuditTrailService auditTrailService,
                               OssStorageClient ossStorageClient,
                               TencentCloudStorageClient tencentCloudStorageClient,
                               OssProperties ossProperties) {
        this.contractRepository = contractRepository;
        this.essApiClient = essApiClient;
        this.properties = properties;
        this.auditTrailService = auditTrailService;
        this.ossStorageClient = ossStorageClient;
        this.tencentCloudStorageClient = tencentCloudStorageClient;
        this.ossProperties = ossProperties;
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

        // 唯一付费调用点的总闸：出证为付费服务，默认关闭。任何调用方（内部/管理后台控制器、
        // 定时重试、manualRetry）到这里都不会触达腾讯付费出证 API，确保零运营成本。
        if (!certificateEnabled) {
            log.debug("出证已禁用，跳过 [contractNo={}]", contract.getContractNo());
            return CertificateResult.disabled(contractId, contract.getContractNo());
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
                String reportUrl = response.has("ReportUrl") ? response.get("ReportUrl").asText() : null;
                if (reportUrl == null || reportUrl.isBlank()) {
                    throw new IllegalStateException("DescribeFlowEvidenceReport 成功但未返回 ReportUrl");
                }
                // ReportUrl 是短效下载链接（腾讯侧约 5 分钟过期），必须下载报告 PDF 落持久存储，
                // 存持久 URL；否则日后下载会失效。失败则抛出→重试（报告状态仍 Success，下轮会重新下载）。
                String durableUrl = archiveEvidenceReport(contract, reportUrl);
                contract.completeCertificate(contract.getEvidenceReportId(), durableUrl); // CERTIFIED
                contractRepository.save(contract);
                auditTrailService.recordSystemEvent(contract.getId(),
                        ContractAuditTrail.Action.CERTIFY_SUCCESS,
                        String.format("{\"reportId\":\"%s\",\"reportUrl\":\"%s\"}",
                                contract.getEvidenceReportId(), durableUrl));
                log.info("出证报告生成成功并已归档 [contractNo={}, reportId={}, url={}]",
                        contract.getContractNo(), contract.getEvidenceReportId(), durableUrl);
                return CertificateResult.success(contract.getId(), contract.getContractNo(),
                        contract.getEvidenceReportId(), durableUrl);
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

    /**
     * 下载短效 ReportUrl 的出证报告 PDF 并落持久存储（腾讯云 COS + 自有 OSS），返回持久 OSS URL。
     */
    private String archiveEvidenceReport(Contract contract, String reportUrl) {
        byte[] pdf = downloadBytes(reportUrl);
        String objectKey = ossProperties.contractPathPrefix() + contract.getContractNo() + "-evidence-report.pdf";
        tencentCloudStorageClient.upload(objectKey, pdf, APPLICATION_PDF);
        String ossUrl = ossStorageClient.upload(objectKey, pdf, APPLICATION_PDF);
        log.info("出证报告已归档 [contractNo={}, size={}KB, ossUrl={}]",
                contract.getContractNo(), pdf.length / 1024, ossUrl);
        return ossUrl;
    }

    /** 下载 URL 字节内容（出证报告 PDF）。包级可见以便测试用 spy 桩替换，避开真实 HTTP。 */
    byte[] downloadBytes(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url)).timeout(Duration.ofSeconds(30)).GET().build();
            HttpResponse<InputStream> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("出证报告下载失败 HTTP " + response.statusCode());
            }
            try (InputStream is = response.body(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
                return bos.toByteArray();
            }
        } catch (Exception e) {
            throw new RuntimeException("出证报告下载异常: " + e.getMessage(), e);
        }
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

        /** 出证功能已禁用（付费服务，未启用）。 */
        public static CertificateResult disabled(Long contractId, String contractNo) {
            return new CertificateResult(false, contractId, contractNo,
                    null, null, "DISABLED", null);
        }
    }
}
