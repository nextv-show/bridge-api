package com.sanshuiyuan.ess.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanshuiyuan.ess.config.EssProperties;
import com.sanshuiyuan.ess.domain.EssFlowRecord;
import com.sanshuiyuan.ess.exception.EssFlowException;
import com.sanshuiyuan.ess.infra.client.EssApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * ESS 文档服务。
 * <p>
 * 提供签署完成后 PDF 文件拉取（DescribeFileUrls）。
 */
@Service
public class EssDocumentService {

    private static final Logger log = LoggerFactory.getLogger(EssDocumentService.class);

    private final EssApiClient apiClient;
    private final EssProperties properties;
    private final EssContractService contractService;
    private final ObjectMapper objectMapper;

    public EssDocumentService(EssApiClient apiClient,
                               EssProperties properties,
                               EssContractService contractService,
                               ObjectMapper objectMapper) {
        this.apiClient = apiClient;
        this.properties = properties;
        this.contractService = contractService;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取签署完成后的 PDF 文件下载 URL 列表。
     *
     * @param contractId 业务合同 ID
     * @return PDF 下载 URL 列表
     */
    @Transactional
    public List<String> getFileUrls(String contractId) {
        EssFlowRecord record = contractService.findByContractId(contractId);

        try {
            TreeMap<String, Object> params = new TreeMap<>();
            params.put("Operator", buildOperator());
            params.put("FlowId", record.getEssFlowId());

            JsonNode response = apiClient.invoke("DescribeFileUrls", params);

            List<String> urls = new ArrayList<>();
            if (response.has("FileUrls") && response.get("FileUrls").isArray()) {
                for (JsonNode node : response.get("FileUrls")) {
                    if (node.has("Url")) {
                        urls.add(node.get("Url").asText());
                    }
                }
            }

            log.info("PDF 文件 URL 获取成功 [contractId={}, count={}]",
                    contractId, urls.size());
            return urls;

        } catch (EssFlowException e) {
            throw e;
        } catch (Exception e) {
            throw new EssFlowException(contractId, record.getEssFlowId(),
                    "获取 PDF 文件 URL 失败: " + e.getMessage());
        }
    }

    /**
     * 获取单个签署文档的下载 URL。
     *
     * @param contractId 业务合同 ID
     * @param fileId     文件 ID
     * @return 下载 URL
     */
    @Transactional
    public String getFileUrl(String contractId, String fileId) {
        EssFlowRecord record = contractService.findByContractId(contractId);

        try {
            TreeMap<String, Object> params = new TreeMap<>();
            params.put("Operator", buildOperator());
            params.put("FlowId", record.getEssFlowId());
            params.put("FileId", fileId);

            JsonNode response = apiClient.invoke("DescribeFileUrl", params);
            String url = response.has("Url") ? response.get("Url").asText() : null;

            log.info("PDF 文件 URL 获取成功 [contractId={}, fileId={}]",
                    contractId, fileId);
            return url;

        } catch (EssFlowException e) {
            throw e;
        } catch (Exception e) {
            throw new EssFlowException(contractId, record.getEssFlowId(),
                    "获取 PDF 文件 URL 失败: " + e.getMessage());
        }
    }

    private ObjectNode buildOperator() {
        ObjectNode operator = objectMapper.createObjectNode();
        operator.put("OperatorId", properties.operatorId());
        operator.put("OperatorType", 1);
        return operator;
    }
}
