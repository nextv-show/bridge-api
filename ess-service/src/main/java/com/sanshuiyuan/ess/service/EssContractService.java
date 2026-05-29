package com.sanshuiyuan.ess.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanshuiyuan.ess.config.EssProperties;
import com.sanshuiyuan.ess.config.SigningPreCheckInterceptor;
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
 * 使用企业版 API（EssClient 模式）：CreateFlow / StartFlow / DescribeFlowInfo 等。
 * 参照官方 ess-java-kit 工具包的参数结构。
 * <p>
 * 关键区别 vs Channel API：
 * - 用 Operator.UserId 代替 Agent
 * - Action 无 Channel 前缀
 * - service=ess, version=2020-11-11
 */
@Service
public class EssContractService {

    private static final Logger log = LoggerFactory.getLogger(EssContractService.class);

    private final EssApiClient apiClient;
    private final EssProperties properties;
    private final EssFlowRecordRepository flowRecordRepository;
    private final EssApiLogService apiLogService;
    private final ObjectMapper objectMapper;
    private final SigningPreCheckInterceptor signingPreCheck;

    public EssContractService(EssApiClient apiClient,
                               EssProperties properties,
                               EssFlowRecordRepository flowRecordRepository,
                               EssApiLogService apiLogService,
                               ObjectMapper objectMapper,
                               SigningPreCheckInterceptor signingPreCheck) {
        this.apiClient = apiClient;
        this.properties = properties;
        this.flowRecordRepository = flowRecordRepository;
        this.apiLogService = apiLogService;
        this.objectMapper = objectMapper;
        this.signingPreCheck = signingPreCheck;
    }

    /**
     * 创建签署流程。
     */
    @Transactional
    public EssFlowRecord createFlow(String contractId, String flowName, String signersJson) {
        flowRecordRepository.findByContractId(contractId).ifPresent(existing -> {
            throw new EssFlowException(contractId, "合同已存在签署流程，flowId=" + existing.getEssFlowId());
        });

        EssFlowRecord record = EssFlowRecord.create(contractId, signersJson);
        flowRecordRepository.save(record);

        long start = System.currentTimeMillis();
        try {
            TreeMap<String, Object> params = new TreeMap<>();
            params.put("Operator", buildOperator());
            params.put("FlowName", flowName);
            // 将业务签署方信息转换为腾讯电子签 Approvers 格式
            params.put("Approvers", buildApprovers(signersJson));
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

    @Transactional
    public void startFlow(String contractId) {
        doStartFlow(contractId, null);
    }

    @Transactional
    public void startFlow(String contractId, Long userId) {
        doStartFlow(contractId, userId);
    }

    private void doStartFlow(String contractId, Long userId) {
        EssFlowRecord record = findFlowRecord(contractId);

        if (userId != null) {
            signingPreCheck.checkIdentityVerified(contractId, userId);
        }

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

    @Transactional
    public FlowStatus describeFlowStatus(String contractId) {
        EssFlowRecord record = findFlowRecord(contractId);

        long start = System.currentTimeMillis();
        try {
            TreeMap<String, Object> params = new TreeMap<>();
            params.put("Operator", buildOperator());
            // DescribeFlowInfo 需要 FlowIds（数组），不是 FlowId
            params.put("FlowIds", java.util.List.of(record.getEssFlowId()));

            JsonNode response = apiClient.invoke("DescribeFlowInfo", params);
            String status = null;
            if (response.has("FlowInfo") && response.get("FlowInfo").has("Status")) {
                status = response.get("FlowInfo").get("Status").asText();
            }

            int duration = (int) (System.currentTimeMillis() - start);
            apiLogService.recordSuccessAsync("DescribeFlowInfo", params.toString(),
                    response.toString(), 200, duration);

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
            apiLogService.recordFailureAsync("DescribeFlowInfo", "{}", null, null,
                    duration, e.getMessage());
            throw new EssFlowException(contractId, record.getEssFlowId(),
                    "查询流程状态失败: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public EssFlowRecord findByEssFlowId(String essFlowId) {
        return flowRecordRepository.findByEssFlowId(essFlowId)
                .orElseThrow(() -> new EssFlowException("unknown", essFlowId, "流程不存在"));
    }

    @Transactional(readOnly = true)
    public EssFlowRecord findByContractId(String contractId) {
        return findFlowRecord(contractId);
    }

    private EssFlowRecord findFlowRecord(String contractId) {
        return flowRecordRepository.findByContractId(contractId)
                .orElseThrow(() -> new EssFlowException(contractId, "签署流程不存在"));
    }

    /**
     * 构造 Operator 参数（企业版 API 风格）。
     * 参照官方 ess-java-kit: userInfo.setUserId(operatorId)
     */
    private TreeMap<String, Object> buildOperator() {
        TreeMap<String, Object> operator = new TreeMap<>();
        operator.put("UserId", properties.operatorId());
        return operator;
    }

    /**
     * 将业务签署方信息 JSON 转换为腾讯电子签 Approvers 数组。
     * <p>
     * 输入格式（业务 JSON）：
     * {"userId":1, "userName":"张三", "idCardNo":"...", "phone":"138...", "role":"PURCHASER"}
     * <p>
     * 输出格式（腾讯 API）：
     * [{"ApproverType":1, "ApproverName":"张三", "ApproverMobile":"138...", "RecipientId":"...", "NotifyType":"NONE"}]
     */
    private java.util.List<TreeMap<String, Object>> buildApprovers(String signerInfoJson) {
        java.util.List<TreeMap<String, Object>> approvers = new java.util.ArrayList<>();
        try {
            JsonNode signers = objectMapper.readTree(signerInfoJson);
            // 兼容单个对象或数组
            if (!signers.isArray()) {
                signers = objectMapper.createArrayNode().add(signers);
            }
            for (JsonNode signer : signers) {
                TreeMap<String, Object> approver = new TreeMap<>();
                approver.put("ApproverType", 1); // 1=个人
                if (signer.has("userName")) {
                    approver.put("ApproverName", signer.get("userName").asText());
                }
                if (signer.has("phone")) {
                    approver.put("ApproverMobile", signer.get("phone").asText());
                }
                // 身份证号：仅在显式开启时传入（生产环境建议开启）
                if (Boolean.TRUE.equals(properties.collectIdCard())
                        && signer.has("idCardNo") && !signer.get("idCardNo").asText().isBlank()) {
                    approver.put("ApproverIdCardNumber", signer.get("idCardNo").asText());
                    approver.put("ApproverIdCardType", "ID_CARD");
                }
                // 使用 operatorId 作为 RecipientId（模板中的签署方 ID）
                approver.put("RecipientId", properties.operatorId());
                approver.put("NotifyType", "NONE");
                approvers.add(approver);
            }
        } catch (Exception e) {
            log.warn("解析签署方信息失败，使用默认签署方: {}", e.getMessage());
            TreeMap<String, Object> defaultApprover = new TreeMap<>();
            defaultApprover.put("ApproverType", 1);
            defaultApprover.put("RecipientId", properties.operatorId());
            defaultApprover.put("NotifyType", "NONE");
            approvers.add(defaultApprover);
        }
        return approvers;
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
