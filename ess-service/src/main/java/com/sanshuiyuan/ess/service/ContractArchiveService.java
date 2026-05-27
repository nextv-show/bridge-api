package com.sanshuiyuan.ess.service;

import com.sanshuiyuan.ess.config.OssProperties;
import com.sanshuiyuan.ess.domain.Contract;
import com.sanshuiyuan.ess.domain.Contract.ArchiveStatus;
import com.sanshuiyuan.ess.domain.Contract.ContractStatus;
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
import java.security.MessageDigest;
import java.time.Duration;

/**
 * 合同归档服务。
 * <p>
 * 签署完成后执行：PDF 拉取 → SHA-256 哈希 → 上传腾讯云端 → 同步 OSS → 更新审计字段。
 * 实现双冗余存储。
 */
@Service
public class ContractArchiveService {

    private static final Logger log = LoggerFactory.getLogger(ContractArchiveService.class);
    private static final String APPLICATION_PDF = "application/pdf";

    private final ContractRepository contractRepository;
    private final EssDocumentService essDocumentService;
    private final OssStorageClient ossStorageClient;
    private final TencentCloudStorageClient tencentCloudStorageClient;
    private final OssProperties ossProperties;
    private final CertificateService certificateService;
    private final AuditTrailService auditTrailService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public ContractArchiveService(ContractRepository contractRepository,
                                   EssDocumentService essDocumentService,
                                   OssStorageClient ossStorageClient,
                                   TencentCloudStorageClient tencentCloudStorageClient,
                                   OssProperties ossProperties,
                                   CertificateService certificateService,
                                   AuditTrailService auditTrailService) {
        this.contractRepository = contractRepository;
        this.essDocumentService = essDocumentService;
        this.ossStorageClient = ossStorageClient;
        this.tencentCloudStorageClient = tencentCloudStorageClient;
        this.ossProperties = ossProperties;
        this.certificateService = certificateService;
        this.auditTrailService = auditTrailService;
    }

    /**
     * 执行合同归档（全流程）。
     * <p>
     * 1. 从腾讯电子签拉取已签署 PDF
     * 2. 计算 SHA-256 哈希
     * 3. 上传到腾讯云端（冗余存储）
     * 4. 同步到自有 OSS
     * 5. 更新合同审计字段
     *
     * @param contractId 合同 ID
     * @return 归档结果
     */
    @Transactional
    public ArchiveResult archiveContract(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("合同不存在: id=" + contractId));

        // 校验状态：只有已签署的合同才能归档
        if (contract.getStatus() != ContractStatus.SIGNED && contract.getStatus() != ContractStatus.ARCHIVED) {
            throw new IllegalStateException(
                    String.format("合同状态为 %s，不允许归档 [contractNo=%s]",
                            contract.getStatus(), contract.getContractNo()));
        }

        if (contract.getArchiveStatus() == ArchiveStatus.ARCHIVED) {
            log.info("合同已归档，跳过 [contractNo={}]", contract.getContractNo());
            return ArchiveResult.alreadyArchived(contractId, contract.getContractNo());
        }

        log.info("开始归档合同 [contractId={}, contractNo={}]", contractId, contract.getContractNo());

        try {
            // 标记归档中
            contract.markArchiving();
            contractRepository.save(contract);

            // 1. 拉取已签署 PDF
            byte[] pdfData = fetchSignedPdf(contract);
            log.info("PDF 拉取成功 [contractNo={}, size={}KB]",
                    contract.getContractNo(), pdfData.length / 1024);

            // 2. 计算 SHA-256 哈希
            String sha256Hash = computeSha256(pdfData);
            log.info("PDF SHA-256 哈希 [contractNo={}, hash={}]", contract.getContractNo(), sha256Hash);

            // 3. 构建 objectKey
            String objectKey = ossProperties.contractPathPrefix() + contract.getContractNo() + ".pdf";

            // 4. 上传到腾讯云端
            String tencentCloudUrl = tencentCloudStorageClient.upload(objectKey, pdfData, APPLICATION_PDF);
            log.info("腾讯云端上传成功 [contractNo={}, url={}]", contract.getContractNo(), tencentCloudUrl);

            // 5. 同步到自有 OSS
            String ossUrl = ossStorageClient.upload(objectKey, pdfData, APPLICATION_PDF);
            log.info("OSS 上传成功 [contractNo={}, url={}]", contract.getContractNo(), ossUrl);

            // 6. 更新审计字段 + 状态归档
            contract.updateArchiveUrls(tencentCloudUrl, ossUrl, sha256Hash);
            contract.archive();
            contractRepository.save(contract);

            log.info("合同归档完成 [contractNo={}, sha256={}]", contract.getContractNo(), sha256Hash);

            // 审计事件：归档成功
            auditTrailService.recordSystemEvent(contractId,
                    com.sanshuiyuan.ess.domain.ContractAuditTrail.Action.ARCHIVE,
                    String.format("{\"sha256\":\"%s\",\"ossUrl\":\"%s\"}", sha256Hash, ossUrl));

            // 7. 归档完成后自动触发待出证标记
            try {
                contract.markPendingCertificate();
                contractRepository.save(contract);
                log.info("合同已标记待出证 [contractNo={}]", contract.getContractNo());
            } catch (Exception e) {
                log.warn("标记待出证失败，不影响归档结果 [contractNo={}]: {}",
                        contract.getContractNo(), e.getMessage());
            }

            return ArchiveResult.success(contractId, contract.getContractNo(),
                    tencentCloudUrl, ossUrl, sha256Hash);

        } catch (Exception e) {
            log.error("合同归档失败 [contractId={}, contractNo={}]: {}",
                    contractId, contract.getContractNo(), e.getMessage(), e);
            contract.markArchiveFailed();
            contractRepository.save(contract);

            // 审计事件：归档失败
            try {
                auditTrailService.recordSystemEvent(contractId,
                        com.sanshuiyuan.ess.domain.ContractAuditTrail.Action.ARCHIVE_FAIL,
                        String.format("{\"error\":\"%s\"}", e.getMessage()));
            } catch (Exception auditEx) {
                log.warn("记录归档失败审计事件异常: {}", auditEx.getMessage());
            }

            throw new RuntimeException("合同归档失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从腾讯电子签拉取已签署 PDF 的字节数据。
     */
    private byte[] fetchSignedPdf(Contract contract) {
        // 首先尝试通过 EssDocumentService 获取 PDF URL
        var urls = essDocumentService.getFileUrls(contract.getContractNo());
        if (urls == null || urls.isEmpty()) {
            throw new RuntimeException("无法获取合同 PDF URL [contractNo=" + contract.getContractNo() + "]");
        }

        String pdfUrl = urls.get(0);

        // 如果合同本身已有 pdfUrl 且 URL 直接可下载，优先使用
        if (contract.getPdfUrl() != null && !contract.getPdfUrl().isBlank()) {
            pdfUrl = contract.getPdfUrl();
        }

        return downloadPdf(pdfUrl);
    }

    /**
     * 下载 PDF 文件。
     */
    byte[] downloadPdf(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                throw new RuntimeException("PDF 下载失败，HTTP " + response.statusCode());
            }

            try (InputStream is = response.body();
                 ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    bos.write(buffer, 0, bytesRead);
                }
                return bos.toByteArray();
            }
        } catch (Exception e) {
            throw new RuntimeException("PDF 下载异常: " + e.getMessage(), e);
        }
    }

    /**
     * 计算 SHA-256 哈希。
     */
    String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 计算失败", e);
        }
    }

    /**
     * 获取合同 PDF 的 OSS 访问 URL（供 H5 查看）。
     * <p>
     * 生成带签名的临时 URL，有效期 3 分钟。
     *
     * @param contractId 合同 ID
     * @return 签名 URL
     */
    @Transactional(readOnly = true)
    public String getViewUrl(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("合同不存在: id=" + contractId));

        if (contract.getArchiveStatus() != ArchiveStatus.ARCHIVED) {
            throw new IllegalStateException("合同尚未归档，无法查看 [contractNo=" + contract.getContractNo() + "]");
        }

        String objectKey = ossProperties.contractPathPrefix() + contract.getContractNo() + ".pdf";
        return ossStorageClient.generatePresignedUrl(objectKey, 180);
    }

    /**
     * 获取合同 PDF 的下载 URL。
     *
     * @param contractId 合同 ID
     * @return 签名 URL
     */
    @Transactional(readOnly = true)
    public String getDownloadUrl(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("合同不存在: id=" + contractId));

        if (contract.getArchiveStatus() != ArchiveStatus.ARCHIVED) {
            throw new IllegalStateException("合同尚未归档，无法下载 [contractNo=" + contract.getContractNo() + "]");
        }

        String objectKey = ossProperties.contractPathPrefix() + contract.getContractNo() + ".pdf";
        return ossStorageClient.generatePresignedUrl(objectKey, 300);
    }

    /**
     * 归档结果。
     */
    public record ArchiveResult(
            boolean success,
            Long contractId,
            String contractNo,
            String tencentCloudUrl,
            String ossUrl,
            String pdfHash,
            String message
    ) {
        public static ArchiveResult success(Long contractId, String contractNo,
                                             String tencentCloudUrl, String ossUrl, String pdfHash) {
            return new ArchiveResult(true, contractId, contractNo,
                    tencentCloudUrl, ossUrl, pdfHash, "归档成功");
        }

        public static ArchiveResult alreadyArchived(Long contractId, String contractNo) {
            return new ArchiveResult(true, contractId, contractNo, null, null, null, "已归档");
        }
    }
}
