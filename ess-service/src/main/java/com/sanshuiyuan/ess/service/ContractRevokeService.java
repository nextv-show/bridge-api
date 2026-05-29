package com.sanshuiyuan.ess.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanshuiyuan.ess.config.EssProperties;
import com.sanshuiyuan.ess.domain.Contract;
import com.sanshuiyuan.ess.domain.Contract.ContractStatus;
import com.sanshuiyuan.ess.domain.ContractAuditTrail;
import com.sanshuiyuan.ess.domain.ContractCooldownRecord;
import com.sanshuiyuan.ess.domain.ContractCooldownRecord.CooldownStatus;
import com.sanshuiyuan.ess.domain.EssFlowRecord;
import com.sanshuiyuan.ess.exception.EssFlowException;
import com.sanshuiyuan.ess.infra.client.EssApiClient;
import com.sanshuiyuan.ess.infra.repository.ContractCooldownRecordRepository;
import com.sanshuiyuan.ess.infra.repository.ContractRepository;
import com.sanshuiyuan.ess.infra.repository.EssFlowRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.TreeMap;

/**
 * 合同撤销服务。
 * <p>
 * 冷静期内撤销合同：
 * 1. 校验冷静期是否有效（ACTIVE 且未过期）
 * 2. 调用腾讯电子签撤回/撤销 API（CancelFlow/RejectFlow）
 * 3. 更新合同状态和冷静期记录
 */
@Service
public class ContractRevokeService {

    private static final Logger log = LoggerFactory.getLogger(ContractRevokeService.class);

    private final ContractCooldownRecordRepository cooldownRecordRepository;
    private final ContractRepository contractRepository;
    private final EssFlowRecordRepository flowRecordRepository;
    private final EssApiClient apiClient;
    private final EssProperties properties;
    private final EssApiLogService apiLogService;
    private final AuditTrailService auditTrailService;
    private final CooldownOrderNotifier orderNotifier;
    private final ObjectMapper objectMapper;

    public ContractRevokeService(ContractCooldownRecordRepository cooldownRecordRepository,
                                  ContractRepository contractRepository,
                                  EssFlowRecordRepository flowRecordRepository,
                                  EssApiClient apiClient,
                                  EssProperties properties,
                                  EssApiLogService apiLogService,
                                  AuditTrailService auditTrailService,
                                  CooldownOrderNotifier orderNotifier,
                                  ObjectMapper objectMapper) {
        this.cooldownRecordRepository = cooldownRecordRepository;
        this.contractRepository = contractRepository;
        this.flowRecordRepository = flowRecordRepository;
        this.apiClient = apiClient;
        this.properties = properties;
        this.apiLogService = apiLogService;
        this.auditTrailService = auditTrailService;
        this.orderNotifier = orderNotifier;
        this.objectMapper = objectMapper;
    }

    /**
     * 撤销合同（冷静期内）。
     * <p>
     * 1. 校验冷静期有效性
     * 2. 调用腾讯电子签撤回 API
     * 3. 更新冷静期状态为 REVOKED
     * 4. 记录审计轨迹
     * 5. 通知订单状态变更
     *
     * @param contractId 合同 ID
     * @param reason     撤销原因
     * @return 撤销结果
     */
    @Transactional
    public RevokeResult revokeContract(Long contractId, String reason) {
        log.info("开始撤销合同 [contractId={}, reason={}]", contractId, reason);

        // 1. 获取冷静期记录
        ContractCooldownRecord cooldownRecord = cooldownRecordRepository.findByContractId(contractId)
                .orElseThrow(() -> new IllegalArgumentException("冷静期记录不存在: contractId=" + contractId));

        // 2. 校验冷静期有效性
        if (cooldownRecord.getStatus() != CooldownStatus.ACTIVE) {
            throw new IllegalStateException(
                    String.format("冷静期状态不允许撤销，当前状态: %s", cooldownRecord.getStatus()));
        }

        if (cooldownRecord.isExpired()) {
            throw new IllegalStateException("冷静期已过期，无法撤销。请通过补充协议退款流程处理。");
        }

        // 3. 获取合同和流程信息
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("合同不存在: id=" + contractId));

        // 4. 调用腾讯电子签撤回 API
        revokeEssFlow(contract, cooldownRecord, reason);

        // 5. 更新冷静期状态
        cooldownRecord.revoke(reason);
        cooldownRecordRepository.save(cooldownRecord);

        // 6. 审计事件
        auditTrailService.recordSystemEvent(contractId,
                ContractAuditTrail.Action.REVOKE,
                String.format("{\"reason\":\"%s\",\"cooldownId\":%d}", reason, cooldownRecord.getId()));

        // 7. 通知订单状态变更
        orderNotifier.notifyCooldownRevoked(cooldownRecord);

        log.info("合同撤销完成 [contractId={}, cooldownId={}]", contractId, cooldownRecord.getId());

        return new RevokeResult(contractId, contract.getContractNo(), true, "撤销成功", reason);
    }

    /**
     * 调用腾讯电子签撤回/撤销 API。
     * <p>
     * 根据合同签署状态选择：
     * - SIGNING 状态：使用 CancelFlow 撤回
     * - DRAFT/GENERATED 状态：无需调用 ESS API
     */
    private void revokeEssFlow(Contract contract, ContractCooldownRecord cooldownRecord, String reason) {
        String essFlowId = contract.getEssFlowId();

        // 如果没有 ESS 流程 ID，跳过 ESS API 调用
        if (essFlowId == null || essFlowId.isBlank()) {
            log.info("合同无 ESS 流程，跳过撤回 API [contractId={}]", contract.getId());
            return;
        }

        long start = System.currentTimeMillis();
        try {
            TreeMap<String, Object> params = new TreeMap<>();
            ObjectNode agent = objectMapper.createObjectNode();
            ObjectNode operator = objectMapper.createObjectNode();
            operator.put("UserId", properties.operatorId());
            params.put("Operator", operator);
            params.put("FlowId", essFlowId);
            params.put("CancelMessage", reason != null ? reason : "用户冷静期内撤销");

            JsonNode response = apiClient.invoke("CancelFlow", params);

            int duration = (int) (System.currentTimeMillis() - start);
            apiLogService.recordSuccessAsync("ChannelCancelFlow", params.toString(),
                    response.toString(), 200, duration);

            log.info("腾讯电子签撤回成功 [contractId={}, flowId={}]", contract.getId(), essFlowId);

            // 同步更新本地流程记录状态
            flowRecordRepository.findByContractId(String.valueOf(contract.getId()))
                    .ifPresent(flowRecord -> {
                        flowRecord.cancel();
                        flowRecordRepository.save(flowRecord);
                    });

        } catch (EssFlowException e) {
            throw e;
        } catch (Exception e) {
            int duration = (int) (System.currentTimeMillis() - start);
            apiLogService.recordFailureAsync("ChannelCancelFlow", "{}", null, null, duration, e.getMessage());
            throw new RuntimeException("腾讯电子签撤回失败: " + e.getMessage(), e);
        }
    }

    /**
     * 撤销结果。
     */
    public record RevokeResult(
            Long contractId,
            String contractNo,
            boolean success,
            String message,
            String reason
    ) {}
}
