package com.sanshuiyuan.ess.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanshuiyuan.ess.config.EssProperties;
import com.sanshuiyuan.ess.domain.EssFlowRecord;
import com.sanshuiyuan.ess.exception.EssFlowException;
import com.sanshuiyuan.ess.infra.client.EssApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.TreeMap;

/**
 * ESS 签署服务。
 * <p>
 * 提供签署 URL 生成（H5/小程序/App）和企业自动签章。
 */
@Service
public class EssSignService {

    private static final Logger log = LoggerFactory.getLogger(EssSignService.class);

    private final EssApiClient apiClient;
    private final EssProperties properties;
    private final EssContractService contractService;
    private final ObjectMapper objectMapper;

    public EssSignService(EssApiClient apiClient,
                           EssProperties properties,
                           EssContractService contractService,
                           ObjectMapper objectMapper) {
        this.apiClient = apiClient;
        this.properties = properties;
        this.contractService = contractService;
        this.objectMapper = objectMapper;
    }

    /**
     * 生成 H5 签署 URL。
     *
     * @param contractId   业务合同 ID
     * @param signerId     签署人 ID
     * @param jumpUrl      签署完成后跳转 URL
     * @param h5Type       H5 类型: "jump" 直跳 / "embed" 嵌入
     * @return 签署 URL
     */
    @Transactional
    public String generateH5SignUrl(String contractId, String signerId,
                                     String jumpUrl, String h5Type) {
        EssFlowRecord record = contractService.findByContractId(contractId);

        long start = System.currentTimeMillis();
        try {
            TreeMap<String, Object> params = new TreeMap<>();
            params.put("Operator", buildOperator());
            params.put("FlowId", record.getEssFlowId());

            ObjectNode signer = objectMapper.createObjectNode();
            signer.put("SignId", signerId);
            params.put("SignId", signerId);

            params.put("JumpUrl", jumpUrl != null ? jumpUrl : "");
            // H5 签署类型：1=直跳，2=嵌入
            int h5TypeInt = "embed".equalsIgnoreCase(h5Type) ? 2 : 1;
            params.put("H5Type", h5TypeInt);

            JsonNode response = apiClient.invoke("CreateH5SignUrl", params);
            String signUrl = response.has("H5SignUrl") ? response.get("H5SignUrl").asText() : null;

            log.info("H5 签署 URL 已生成 [contractId={}, signerId={}]", contractId, signerId);
            return signUrl;

        } catch (EssFlowException e) {
            throw e;
        } catch (Exception e) {
            throw new EssFlowException(contractId, record.getEssFlowId(),
                    "生成 H5 签署 URL 失败: " + e.getMessage());
        }
    }

    /**
     * 生成小程序签署参数。
     *
     * @param contractId   业务合同 ID
     * @param signerId     签署人 ID
     * @param wxAppId      微信小程序 AppID
     * @return 小程序签署参数 JSON
     */
    @Transactional
    public JsonNode generateMiniAppSignParams(String contractId, String signerId,
                                               String wxAppId) {
        EssFlowRecord record = contractService.findByContractId(contractId);

        long start = System.currentTimeMillis();
        try {
            TreeMap<String, Object> params = new TreeMap<>();
            params.put("Operator", buildOperator());
            params.put("FlowId", record.getEssFlowId());
            params.put("SignId", signerId);
            if (wxAppId != null) {
                params.put("WxAppId", wxAppId);
            }

            JsonNode response = apiClient.invoke("CreateMiniAppSignParams", params);

            log.info("小程序签署参数已生成 [contractId={}, signerId={}]", contractId, signerId);
            return response;

        } catch (EssFlowException e) {
            throw e;
        } catch (Exception e) {
            throw new EssFlowException(contractId, record.getEssFlowId(),
                    "生成小程序签署参数失败: " + e.getMessage());
        }
    }

    /**
     * 生成 App 签署参数（WebView/原生插件桥接）。
     *
     * @param contractId   业务合同 ID
     * @param signerId     签署人 ID
     * @param appType      App 类型: "android" / "ios"
     * @return App 签署参数 JSON
     */
    @Transactional
    public JsonNode generateAppSignParams(String contractId, String signerId, String appType) {
        EssFlowRecord record = contractService.findByContractId(contractId);

        try {
            TreeMap<String, Object> params = new TreeMap<>();
            params.put("Operator", buildOperator());
            params.put("FlowId", record.getEssFlowId());
            params.put("SignId", signerId);
            params.put("AppType", appType);

            JsonNode response = apiClient.invoke("CreateAppSignParams", params);

            log.info("App 签署参数已生成 [contractId={}, appType={}]", contractId, appType);
            return response;

        } catch (EssFlowException e) {
            throw e;
        } catch (Exception e) {
            throw new EssFlowException(contractId, record.getEssFlowId(),
                    "生成 App 签署参数失败: " + e.getMessage());
        }
    }

    /**
     * 企业自动签章（服务端签署）。
     *
     * @param contractId 业务合同 ID
     */
    @Transactional
    public void createServerSign(String contractId) {
        EssFlowRecord record = contractService.findByContractId(contractId);

        try {
            TreeMap<String, Object> params = new TreeMap<>();
            params.put("Operator", buildOperator());
            params.put("FlowId", record.getEssFlowId());

            apiClient.invoke("CreateServerSign", params);

            log.info("企业自动签章完成 [contractId={}, flowId={}]",
                    contractId, record.getEssFlowId());

        } catch (EssFlowException e) {
            throw e;
        } catch (Exception e) {
            throw new EssFlowException(contractId, record.getEssFlowId(),
                    "企业自动签章失败: " + e.getMessage());
        }
    }

    /**
     * 按合同 ID 查找流程记录（委托给 ContractService）。
     */
    @Transactional(readOnly = true)
    public EssFlowRecord findByContractId(String contractId) {
        return contractService.findByContractId(contractId);
    }

    private ObjectNode buildOperator() {
        ObjectNode operator = objectMapper.createObjectNode();
        operator.put("OperatorId", properties.operatorId());
        operator.put("OperatorType", 1);
        return operator;
    }
}
