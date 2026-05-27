package com.sanshuiyuan.ess.service;

import com.sanshuiyuan.ess.domain.Contract;
import com.sanshuiyuan.ess.infra.repository.ContractRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/**
 * 跨端合同一致性校验服务。
 * <p>
 * 保证 H5/小程序/App 三端查看合同 PDF 时：
 * 1. 使用统一的 OSS PDF URL（唯一真实来源）
 * 2. 通过 PDF 哈希校验数据完整性
 * 3. 跨端签署结果一致
 */
@Service
public class CrossPlatformConsistencyService {

    private static final Logger log = LoggerFactory.getLogger(CrossPlatformConsistencyService.class);

    private final ContractRepository contractRepository;
    private final ContractArchiveService archiveService;
    private final ContractQueryService queryService;

    public CrossPlatformConsistencyService(ContractRepository contractRepository,
                                            ContractArchiveService archiveService,
                                            ContractQueryService queryService) {
        this.contractRepository = contractRepository;
        this.archiveService = archiveService;
        this.queryService = queryService;
    }

    /**
     * 跨端合同查看结果。
     */
    public record ContractViewResult(
            Long contractId,
            String contractNo,
            String status,
            String signSource,
            String viewUrl,
            String pdfHash,
            boolean hashVerified,
            String ossUrl
    ) {}

    /**
     * 获取跨端统一的合同查看信息。
     * <p>
     * 任意端（H5/小程序/App）调用此方法，返回相同的：
     * - OSS PDF URL（统一来源）
     * - PDF 哈希值（完整性校验）
     * - 签署来源信息
     *
     * @param contractId 合同 ID
     * @return 跨端统一的合同查看结果
     */
    @Transactional(readOnly = true)
    public ContractViewResult getUnifiedContractView(Long contractId) {
        Contract contract = queryService.getContractDetail(contractId);

        // 统一 OSS URL 为唯一真实来源
        String viewUrl = "";
        if (contract.getArchiveStatus() == Contract.ArchiveStatus.ARCHIVED) {
            viewUrl = archiveService.getViewUrl(contractId);
        } else if (contract.getPdfUrl() != null && !contract.getPdfUrl().isBlank()) {
            viewUrl = contract.getPdfUrl();
        }

        // PDF 哈希校验
        boolean hashVerified = verifyPdfHash(contract);

        String signSource = contract.getSignSource() != null
                ? contract.getSignSource().name() : "";

        log.info("跨端合同查看 [contractId={}, signSource={}, hashVerified={}]",
                contractId, signSource, hashVerified);

        return new ContractViewResult(
                contractId,
                contract.getContractNo(),
                contract.getStatus().name(),
                signSource,
                viewUrl,
                contract.getPdfHash() != null ? contract.getPdfHash() : "",
                hashVerified,
                contract.getOssUrl() != null ? contract.getOssUrl() : ""
        );
    }

    /**
     * 校验 PDF 哈希值是否一致。
     * <p>
     * 比较合同记录中的 pdfHash 与归档时记录的 pdfHash，
     * 确保签署结果跨端一致。
     *
     * @param contractId 合同 ID
     * @param expectedHash 期望的哈希值
     * @return 校验结果
     */
    @Transactional(readOnly = true)
    public HashVerificationResult verifyPdfHash(Long contractId, String expectedHash) {
        Contract contract = queryService.getContractDetail(contractId);

        if (contract.getPdfHash() == null || contract.getPdfHash().isBlank()) {
            return new HashVerificationResult(contractId, false,
                    "合同尚无 PDF 哈希记录", contract.getPdfHash(), expectedHash);
        }

        boolean match = contract.getPdfHash().equalsIgnoreCase(expectedHash);

        log.info("PDF 哈希校验 [contractId={}, match={}]", contractId, match);

        return new HashVerificationResult(
                contractId,
                match,
                match ? "哈希校验通过" : "哈希不匹配，PDF 可能被篡改",
                contract.getPdfHash(),
                expectedHash
        );
    }

    /**
     * 内部哈希校验（无外部 expectedHash 时，校验 pdfHash 是否存在且不为空）。
     */
    private boolean verifyPdfHash(Contract contract) {
        if (contract.getPdfHash() == null || contract.getPdfHash().isBlank()) {
            // 未签署完成的合同，哈希为空属于正常
            return contract.getStatus() == Contract.ContractStatus.DRAFT
                    || contract.getStatus() == Contract.ContractStatus.GENERATED
                    || contract.getStatus() == Contract.ContractStatus.SIGNING;
        }
        // 已签署的合同有 pdfHash 即视为通过
        return true;
    }

    /**
     * 哈希校验结果。
     */
    public record HashVerificationResult(
            Long contractId,
            boolean verified,
            String message,
            String actualHash,
            String expectedHash
    ) {}
}
