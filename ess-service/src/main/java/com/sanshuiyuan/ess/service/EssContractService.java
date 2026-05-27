package com.sanshuiyuan.ess.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanshuiyuan.ess.config.EssProperties;
import com.sanshuiyuan.ess.domain.EssFlowRecord;
import com.sanshuiyuan.ess.domain.FlowStatus;
import com.sanshuiyuan.ess.exception.EssFlowException;
import com.sanshuiyuan.ess.infra.client.EssApiClient;
import com.sanshuiyuan.ess.infra.repository.EssFlowRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.TreeMap;

/**
 * ESS 合同/流程服务。
 * <p>
 * 封装 CreateFlow、StartFlow、DescribeFlowStatus 等核心流程操作。
 */
@Service
public class EssContractService {

    private static final Logger log = LoggerFactory.getLogger(EssContractService.class);

    private final EssApiClient apiClient;
    private final EssProperties properties;
    private final EssFlowRecordRepository flowRecordRepository;
    private final EssApiLogService apiLogService;
    private final ObjectMapper objectMapper;

    public EssContractService(EssApiClient apiClient,
                               EssProperties properties,
                               EssFlowRecordRepository flowRecordRepository,
                               EssApiLogService apiLogService,
                               ObjectMapper objectMapper) {
        this.apiClient = apiClient;
        this.properties = properties;
        this.flowRecordRepository = flowRecordRepository;
        this.apiLogService = apiLogService;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建签署流程。
     *
     * @param contractId   业务合同 ID
     * @param flowName     流程名称
     * @param signersJson  签署方 JSON 数组
     * @return 流程记录
     */
    @Transactional
    public EssFlowRecord createFlow(String contractId, String flowName, String signersJson) {
        // 检查是否已有流程
        flowRecordRepository.findByContractId(contractId).ifPresent(existing -> {
            throw new EssFlowException(contractId, "合同已存在签署流程，flowId=" + existing.getEssFlowId());
        });

        // 创建本地记录
        EssFlowRecord record = EssFlowRecord.create(contractId, signersJson);
        flowRecordRepository.save(record);

        // 调用 ESS API
        long start = System.currentTimeMillis();
        try {
            TreeMap<String, Object> params = new TreeMap<>();
            params.put("Operator", buildOperator());
            params.put("FlowName", flowName);
            params.put("FlowApprovers", signersJson);
            if (properties.templateId() != null && !properties.templateId().isBlank()) {
                params.put("TemplateId", properties.templateId());
            }

            JsonNode response = apiClient.invoke("CreateFlow", params);
            String flowId = response.get("FlowId").asText();

            record.assignFlowId(flowId);
            flowRecordRepository.save(record);

            int duration = (int) (System.currentTimeMillis() - start);
            apiLogService.recordSuccessAsync("CreateFlow", params.toString(), response.toString(), 200, duration);

            log.info("签署流程已创建 [contractId={}, flowId={}]", contractId, flowId);
            return record;

        } catch (EssFlowException e) {
            throw e;
        } catch (Exception e) {
            int duration = (int) (System.currentTimeMillis() - start);
            apiLogService.recordFailureAsync("CreateFlow", "{}", null, null, duration, e.getMessage());
            throw new EssFlowException(contractId, "创建签署流程失败: " + e.getMessage());
        }
    }

    /**
     * 启动签署流程。
     *
     * @param contractId 业务合同 ID
     */
    @Transactional
    public void startFlow(String contractId) {
        EssFlowRecord record = findFlowRecord(contractId);

        if (record.getFlowStatus() != FlowStatus.CREATED) {
            throw new EssFlowException(contractId, record.getEssFlowId(),
                    "流程状态不允许启动，当前状态: " + record.getFlowStatus());
        }

        long start = System.currentTimeMillis();
        try {
            TreeMap<String, Object> params = new TreeMap<>();
            params.put("Operator", buildOperator());
            params.put("FlowId", record.getEssFlowId());

            JsonNode response = apiClient.invoke("StartFlow", params);

            record.startSigning();
            flowRecordRepository.save(record);

            int duration = (int) (System.currentTimeMillis() - start);
            apiLogService.recordSuccessAsync("StartFlow", params.toString(), response.toString(), 200, duration);

            log.info("签署流程已启动 [contractId={}, flowId={}]", contractId, record.getEssFlowId());

        } catch (EssFlowException e) {
            throw e;
        } catch (Exception e) {
            int duration = (int) (System.currentTimeMillis() - start);
            apiLogService.recordFailureAsync("StartFlow", "{}", null, null, duration, e.getMessage());
            throw new EssFlowException(contractId, record.getEssFlowId(),
                    "启动签署流程失败: " + e.getMessage());
        }
    }

    /**
     * 查询签署流程状态。
     *
     * @param contractId 业务合同 ID
     * @return 流程状态
     */
    @Transactional
    public FlowStatus describeFlowStatus(String contractId) {
        EssFlowRecord record = findFlowRecord(contractId);

        long start = System.currentTimeMillis();
        try {
            TreeMap<String, Object> params = new TreeMap<>();
            params.put("Operator", buildOperator());
            params.put("FlowId", record.getEssFlowId());

            JsonNode response = apiClient.invoke("DescribeFlowStatus", params);
            String status = response.has("FlowStatus") ? response.get("FlowStatus").asText() : null;

            int duration = (int) (System.currentTimeMillis() - start);
            apiLogService.recordSuccessAsync("DescribeFlowStatus", params.toString(),
                    response.toString(), 200, duration);

            // 映射 ESS 状态到本地状态
            FlowStatus mapped = mapEssStatus(status);
            if (mapped != null && mapped != record.getFlowStatus()) {
                updateRecordStatus(record, mapped, response.toString());
            }

            log.debug("流程状态查询完成 [contractId={}, essStatus={}, localStatus={}]",
                    contractId, status, record.getFlowStatus());
            return record.getFlowStatus();

        } catch (EssFlowException e) {
            throw e;
        } catch (Exception e) {
            int duration = (int) (System.currentTimeMillis() - start);
            apiLogService.recordFailureAsync("DescribeFlowStatus", "{}", null, null,
                    duration, e.getMessage());
            throw new EssFlowException(contractId, record.getEssFlowId(),
                    "查询流程状态失败: " + e.getMessage());
        }
    }

    /**
     * 根据 ESS Flow ID 查询流程记录。
     */
    @Transactional(readOnly = true)
    public EssFlowRecord findByEssFlowId(String essFlowId) {
        return flowRecordRepository.findByEssFlowId(essFlowId)
                .orElseThrow(() -> new EssFlowException("unknown", essFlowId, "流程不存在"));
    }

    /**
     * 根据合同 ID 查询流程记录（公开方法）。
     */
    @Transactional(readOnly = true)
    public EssFlowRecord findByContractId(String contractId) {
        return findFlowRecord(contractId);
    }

    private EssFlowRecord findFlowRecord(String contractId) {
        return flowRecordRepository.findByContractId(contractId)
                .orElseThrow(() -> new EssFlowException(contractId, "签署流程不存在"));
    }

    private ObjectNode buildOperator() {
        ObjectNode operator = objectMapper.createObjectNode();
        operator.put("OperatorId", properties.operatorId());
        operator.put("OperatorType", 1); // 1=企业
        return operator;
    }

    private FlowStatus mapEssStatus(String essStatus) {
        if (essStatus == null) return null;
        return switch (essStatus) {
            case "0", "INIT" -> FlowStatus.INIT;
            case "1", "CREATED" -> FlowStatus.CREATED;
            case "2", "SIGNING" -> FlowStatus.SIGNING;
            case "3", "COMPLETED", "FINISH" -> FlowStatus.COMPLETED;
            case "4", "CANCELLED" -> FlowStatus.CANCELLED;
            case "5", "REJECTED" -> FlowStatus.REJECTED;
            case "6", "EXPIRED" -> FlowStatus.EXPIRED;
            default -> null;
        };
    }

    private void updateRecordStatus(EssFlowRecord record, FlowStatus status, String callbackData) {
        switch (status) {
            case COMPLETED -> record.complete(callbackData);
            case CANCELLED -> record.cancel();
            case REJECTED -> record.reject(callbackData);
            case ERROR -> record.markError(callbackData);
            default -> { /* other statuses handled implicitly */ }
        }
        flowRecordRepository.save(record);
    }
}
