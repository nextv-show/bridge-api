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
import org.springframework.transaction.annotation.Propagation;
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
    // NOT_SUPPORTED：挂起调用方事务、本方法不开事务运行。
    // 否则当桥接/对账（describeFlowStatus 的 @Transactional 内）调用本方法时，DescribeFileUrls 失败会把
    // 调用方事务标记 rollback-only，连同刚写入的 SIGNED/COMPLETED 一起回滚（即使上层 catch 也无效）。
    // PDF/归档是签署完成的下游，失败绝不能反向回滚签署状态。
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<String> getFileUrls(String contractId) {
        EssFlowRecord record = contractService.findByContractId(contractId);

        try {
            // 企业版 DescribeFileUrls：Operator + BusinessType=FLOW + BusinessIds=[flowId]。
            // （旧代码错传 Agent + FlowId(单数) → 腾讯报「businessIds 不能为空」。）
            TreeMap<String, Object> params = new TreeMap<>();
            params.put("Operator", buildOperator());
            params.put("BusinessType", "FLOW");
            params.put("BusinessIds", java.util.List.of(record.getEssFlowId()));

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
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public String getFileUrl(String contractId, String fileId) {
        EssFlowRecord record = contractService.findByContractId(contractId);

        try {
            TreeMap<String, Object> params = new TreeMap<>();
            ObjectNode agent = objectMapper.createObjectNode();
            agent.put("AppId", properties.corpId());
            params.put("Agent", agent);
            params.put("Operator", buildOperator());
            params.put("FlowId", record.getEssFlowId());
            params.put("FileId", fileId);

            JsonNode response = apiClient.invoke("DescribeFileUrls", params);
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
        operator.put("UserId", properties.operatorId());
        return operator;
    }
}
