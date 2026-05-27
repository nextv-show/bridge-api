package com.sanshuiyuan.ess.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sanshuiyuan.ess.config.EssProperties;
import com.sanshuiyuan.ess.domain.Contract;
import com.sanshuiyuan.ess.domain.Contract.CertificateStatus;
import com.sanshuiyuan.ess.domain.Contract.ContractStatus;
import com.sanshuiyuan.ess.infra.client.EssApiClient;
import com.sanshuiyuan.ess.infra.repository.ContractRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.TreeMap;

/**
 * 出证服务。
 * <p>
 * 调用腾讯电子签 CreateCertificate 或类似出证 API，
 * 将出证结果绑定到合同记录。
 */
@Service
public class CertificateService {

    private static final Logger log = LoggerFactory.getLogger(CertificateService.class);

    private final ContractRepository contractRepository;
    private final EssApiClient essApiClient;
    private final EssProperties properties;

    public CertificateService(ContractRepository contractRepository,
                               EssApiClient essApiClient,
                               EssProperties properties) {
        this.contractRepository = contractRepository;
        this.essApiClient = essApiClient;
        this.properties = properties;
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

        log.info("开始申请出证 [contractId={}, contractNo={}]", contractId, contract.getContractNo());

        try {
            // 标记出证中
            contract.markCertifying();
            contractRepository.save(contract);

            // 调用腾讯电子签出证 API
            TreeMap<String, Object> params = buildCertifyParams(contract);
            JsonNode response = essApiClient.invoke("CreateCertificate", params);

            // 解析出证结果
            String certificateNo = extractCertificateNo(response);
            String certificatePdfUrl = extractCertificatePdfUrl(response);

            // 更新合同出证信息
            contract.completeCertificate(certificateNo, certificatePdfUrl);
            contractRepository.save(contract);

            log.info("出证成功 [contractNo={}, certificateNo={}]",
                    contract.getContractNo(), certificateNo);

            return CertificateResult.success(contractId, contract.getContractNo(),
                    certificateNo, certificatePdfUrl);

        } catch (Exception e) {
            log.error("出证失败 [contractId={}, contractNo={}]: {}",
                    contractId, contract.getContractNo(), e.getMessage(), e);
            contract.markCertificateFailed();
            contractRepository.save(contract);
            throw new RuntimeException("出证失败: " + e.getMessage(), e);
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
     * 构建出证 API 请求参数。
     */
    private TreeMap<String, Object> buildCertifyParams(Contract contract) {
        TreeMap<String, Object> params = new TreeMap<>();
        params.put("Agent", buildAgent());
        params.put("FlowId", contract.getEssFlowId());
        return params;
    }

    private TreeMap<String, Object> buildAgent() {
        TreeMap<String, Object> agent = new TreeMap<>();
        agent.put("ProxyOrganizationOpenId", properties.corpId());
        agent.put("ProxyOperatorId", properties.operatorId());
        return agent;
    }

    private String extractCertificateNo(JsonNode response) {
        if (response != null && response.has("CertificateId")) {
            return response.get("CertificateId").asText();
        }
        if (response != null && response.has("CertificateNo")) {
            return response.get("CertificateNo").asText();
        }
        return "CERT-" + System.currentTimeMillis();
    }

    private String extractCertificatePdfUrl(JsonNode response) {
        if (response != null && response.has("CertificateUrl")) {
            return response.get("CertificateUrl").asText();
        }
        if (response != null && response.has("PdfUrl")) {
            return response.get("PdfUrl").asText();
        }
        return null;
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
    }
}
