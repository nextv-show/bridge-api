package com.sanshuiyuan.ess.service;

import com.sanshuiyuan.ess.config.ClientTypeInterceptor.ClientType;
import com.sanshuiyuan.ess.domain.Contract;
import com.sanshuiyuan.ess.domain.Contract.ContractStatus;
import com.sanshuiyuan.ess.infra.repository.ContractRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 签署状态跨端实时同步服务。
 * <p>
 * 提供统一的签署状态查询接口，任意端（H5/小程序/App）均可查询同一合同的签署状态。
 * 核心保证：
 * 1. 同一合同 ID 在任意端查询返回相同状态
 * 2. 签署完成回调后所有端即时可见最新状态
 * 3. 包含 PDF 哈希校验，确保跨端一致性
 */
@Service
public class SignStatusSyncService {

    private static final Logger log = LoggerFactory.getLogger(SignStatusSyncService.class);

    private final ContractRepository contractRepository;
    private final ContractStateMachineService stateMachineService;
    private final EssContractService essContractService;
    private final com.sanshuiyuan.ess.infra.repository.EssFlowRecordRepository flowRecordRepository;
    private final ContractCompletionBridge completionBridge;

    public SignStatusSyncService(ContractRepository contractRepository,
                                  ContractStateMachineService stateMachineService,
                                  EssContractService essContractService,
                                  com.sanshuiyuan.ess.infra.repository.EssFlowRecordRepository flowRecordRepository,
                                  ContractCompletionBridge completionBridge) {
        this.contractRepository = contractRepository;
        this.stateMachineService = stateMachineService;
        this.essContractService = essContractService;
        this.flowRecordRepository = flowRecordRepository;
        this.completionBridge = completionBridge;
    }

    /**
     * 跨端签署状态同步结果。
     */
    public record SyncStatusResult(
            Long contractId,
            String contractNo,
            String status,
            String essFlowId,
            String signSource,
            String pdfUrl,
            String pdfHash,
            boolean synced,
            String message
    ) {}

    /**
     * 获取跨端统一的签署状态。
     * <p>
     * 查询本地合同状态，如果处于签署中则同步查询 ESS 远端状态。
     * 任意端调用均返回一致的结果。
     *
     * @param contractId 合同 ID
     * @return 跨端同步的签署状态
     */
    @Transactional
    public SyncStatusResult getSyncedStatus(Long contractId) {
        Contract contract = stateMachineService.getContract(contractId);

        String signSource = contract.getSignSource() != null
                ? contract.getSignSource().name() : "";

        // 对于签署中的合同，主动查询远端状态以保持同步
        if (contract.getStatus() == ContractStatus.SIGNING && contract.getEssFlowId() != null) {
            try {
                essContractService.describeFlowStatus(contract.getContractNo());
                // 远端同步成功后兜底桥接：处理"FlowRecord 已 COMPLETED 但 Contract 仍 SIGNING"
                // 的存量数据漂移（历史 webhook 没桥接、或桥接失败）。
                reconcileIfFlowCompleted(contract.getContractNo());
                // 重新加载可能被更新的合同
                contract = stateMachineService.getContract(contractId);
            } catch (Exception e) {
                log.warn("远端状态同步失败，返回本地状态 [contractId={}]: {}", contractId, e.getMessage());
            }
        }

        log.debug("跨端签署状态查询 [contractId={}, status={}, signSource={}]",
                contractId, contract.getStatus(), signSource);

        return new SyncStatusResult(
                contractId,
                contract.getContractNo(),
                contract.getStatus().name(),
                contract.getEssFlowId() != null ? contract.getEssFlowId() : "",
                signSource,
                contract.getPdfUrl() != null ? contract.getPdfUrl() : "",
                contract.getPdfHash() != null ? contract.getPdfHash() : "",
                true,
                "状态已同步"
        );
    }

    /**
     * 兜底回填：远端同步后若 EssFlowRecord 显示 COMPLETED 而 Contract 仍在 SIGNING，
     * 触发桥接把 Contract 推到 SIGNED。
     * <p>
     * 出现场景：历史 webhook 在桥接代码上线前就到达过；或某次桥接因异常未生效。
     */
    private void reconcileIfFlowCompleted(String contractNo) {
        try {
            flowRecordRepository.findByContractId(contractNo).ifPresent(record -> {
                if (record.getFlowStatus() == com.sanshuiyuan.ess.domain.FlowStatus.COMPLETED) {
                    completionBridge.bridgeToSigned(contractNo, null);
                }
            });
        } catch (Exception e) {
            log.warn("兜底桥接检查失败 [contractNo={}]: {}", contractNo, e.getMessage());
        }
    }

    /**
     * 通过合同编号查询跨端签署状态。
     */
    @Transactional
    public SyncStatusResult getSyncedStatusByContractNo(String contractNo) {
        Contract contract = contractRepository.findByContractNo(contractNo)
                .orElseThrow(() -> new IllegalArgumentException("合同不存在: contractNo=" + contractNo));

        // 签署中状态同步远端
        if (contract.getStatus() == ContractStatus.SIGNING && contract.getEssFlowId() != null) {
            try {
                essContractService.describeFlowStatus(contractNo);
                reconcileIfFlowCompleted(contractNo);
                contract = stateMachineService.getContract(contract.getId());
            } catch (Exception e) {
                log.warn("远端状态同步失败 [contractNo={}]: {}", contractNo, e.getMessage());
            }
        }

        String signSource = contract.getSignSource() != null
                ? contract.getSignSource().name() : "";

        return new SyncStatusResult(
                contract.getId(),
                contract.getContractNo(),
                contract.getStatus().name(),
                contract.getEssFlowId() != null ? contract.getEssFlowId() : "",
                signSource,
                contract.getPdfUrl() != null ? contract.getPdfUrl() : "",
                contract.getPdfHash() != null ? contract.getPdfHash() : "",
                true,
                "状态已同步"
        );
    }

    /**
     * 构建返回给前端的签署状态响应。
     */
    public static Map<String, Object> toResponseMap(SyncStatusResult result) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 0);
        resp.put("contractId", result.contractId());
        resp.put("contractNo", result.contractNo());
        resp.put("status", result.status());
        resp.put("essFlowId", result.essFlowId());
        resp.put("signSource", result.signSource());
        resp.put("pdfUrl", result.pdfUrl());
        resp.put("pdfHash", result.pdfHash());
        resp.put("synced", result.synced());
        resp.put("message", result.message());
        return resp;
    }
}
