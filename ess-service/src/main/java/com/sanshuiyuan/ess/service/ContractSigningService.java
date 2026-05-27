package com.sanshuiyuan.ess.service;

import com.sanshuiyuan.ess.domain.Contract;
import com.sanshuiyuan.ess.domain.Contract.ContractStatus;
import com.sanshuiyuan.ess.domain.ContractAuditTrail;
import com.sanshuiyuan.ess.domain.ContractSnBinding;
import com.sanshuiyuan.ess.domain.EssFlowRecord;
import com.sanshuiyuan.ess.infra.repository.ContractRepository;
import com.sanshuiyuan.ess.infra.repository.ContractSnBindingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 合同签署编排服务。
 * <p>
 * 协调合同状态机与腾讯电子签流程的签署全流程。
 */
@Service
public class ContractSigningService {

    private static final Logger log = LoggerFactory.getLogger(ContractSigningService.class);

    private final ContractRepository contractRepository;
    private final ContractSnBindingRepository snBindingRepository;
    private final EssContractService essContractService;
    private final ContractStateMachineService stateMachineService;
    private final ContractArchiveService archiveService;
    private final AuditTrailService auditTrailService;

    public ContractSigningService(ContractRepository contractRepository,
                                   ContractSnBindingRepository snBindingRepository,
                                   EssContractService essContractService,
                                   ContractStateMachineService stateMachineService,
                                   ContractArchiveService archiveService,
                                   AuditTrailService auditTrailService) {
        this.contractRepository = contractRepository;
        this.snBindingRepository = snBindingRepository;
        this.essContractService = essContractService;
        this.stateMachineService = stateMachineService;
        this.archiveService = archiveService;
        this.auditTrailService = auditTrailService;
    }

    /**
     * 发起签署流程。
     * <p>
     * 1. 校验合同状态为 GENERATED
     * 2. 调用腾讯电子签创建签署流程
     * 3. 更新合同状态为 SIGNING
     *
     * @param contractId 合同 ID
     * @param userId     用户 ID（用于身份核验校验）
     * @return 签署流程信息
     */
    @Transactional
    public SigningInitiationResult initiateSigning(Long contractId, Long userId) {
        return initiateSigning(contractId, userId, null);
    }

    /**
     * 发起签署流程（带签署来源）。
     *
     * @param contractId 合同 ID
     * @param userId     用户 ID
     * @param signSource 签署来源 (H5/MINI/APP)，为 null 时保持旧逻辑
     * @return 签署流程信息
     */
    @Transactional
    public SigningInitiationResult initiateSigning(Long contractId, Long userId,
                                                    Contract.SignSource signSource) {
        Contract contract = stateMachineService.getContract(contractId);

        // 校验状态
        if (!contract.getStatus().canTransitionTo(ContractStatus.SIGNING)) {
            throw new IllegalStateException(
                    String.format("合同当前状态为 %s，无法发起签署 [contractNo=%s]",
                            contract.getStatus(), contract.getContractNo()));
        }

        log.info("发起签署流程 [contractId={}, contractNo={}, userId={}, signSource={}]",
                contractId, contract.getContractNo(), userId, signSource);

        // 调用腾讯电子签创建签署流程
        String contractNoStr = contract.getContractNo();
        String flowName = "三水元设备合同签署-" + contractNoStr;

        EssFlowRecord flowRecord = essContractService.createFlow(
                contractNoStr, flowName, contract.getSignerInfoJson());

        // 更新合同状态（带签署来源）
        if (signSource != null) {
            contract.startSigning(flowRecord.getEssFlowId(), signSource);
        } else {
            contract.startSigning(flowRecord.getEssFlowId());
        }
        contractRepository.save(contract);

        log.info("签署流程已创建 [contractNo={}, essFlowId={}, signSource={}]",
                contract.getContractNo(), flowRecord.getEssFlowId(), signSource);

        // 审计事件：开始签署
        String auditMeta = signSource != null
                ? String.format("{\"essFlowId\":\"%s\",\"signSource\":\"%s\"}",
                    flowRecord.getEssFlowId(), signSource.name())
                : String.format("{\"essFlowId\":\"%s\"}", flowRecord.getEssFlowId());
        auditTrailService.recordSystemEvent(contractId,
                ContractAuditTrail.Action.START_SIGN, auditMeta);

        return new SigningInitiationResult(
                contractId, contract.getContractNo(),
                flowRecord.getEssFlowId(), ContractStatus.SIGNING, signSource);
    }

    /**
     * 签署完成回调处理。
     * <p>
     * 1. 更新合同状态为 SIGNED
     * 2. 确认 SN 绑定
     * 3. 自动触发归档流程
     *
     * @param contractId 合同 ID
     * @param pdfUrl     合同 PDF 地址
     * @param pdfHash    合同 PDF 哈希
     */
    @Transactional
    public void completeSigning(Long contractId, String pdfUrl, String pdfHash) {
        Contract contract = stateMachineService.getContract(contractId);

        contract.completeSigning(pdfUrl, pdfHash);
        contract.markPendingArchive();
        contractRepository.save(contract);

        // 确认 SN 绑定
        snBindingRepository.findByContractId(contractId).forEach(binding -> {
            if (binding.getBindingType() == ContractSnBinding.BindingType.PRE_ALLOCATED) {
                binding.confirm();
                snBindingRepository.save(binding);
            }
        });

        log.info("合同签署完成，触发归档 [contractNo={}, pdfUrl={}]", contract.getContractNo(), pdfUrl);

        // 审计事件：签署完成
        auditTrailService.recordSystemEvent(contractId,
                ContractAuditTrail.Action.SIGN_COMPLETE,
                String.format("{\"pdfHash\":\"%s\"}", pdfHash));

        // 自动触发归档
        try {
            archiveService.archiveContract(contractId);
        } catch (Exception e) {
            log.error("签署后自动归档失败，将等待重试 [contractId={}]: {}", contractId, e.getMessage());
            // 归档失败不影响签署完成状态，后续重试机制处理
        }
    }

    /**
     * 归档合同。
     */
    @Transactional
    public void archiveContract(Long contractId) {
        Contract contract = stateMachineService.getContract(contractId);
        contract.archive();
        contractRepository.save(contract);

        log.info("合同已归档 [contractNo={}]", contract.getContractNo());
    }

    /**
     * 签署发起结果。
     */
    public record SigningInitiationResult(
            Long contractId,
            String contractNo,
            String essFlowId,
            ContractStatus status,
            Contract.SignSource signSource
    ) {
        /**
         * 兼容旧调用方（无 signSource）。
         */
        public SigningInitiationResult(Long contractId, String contractNo,
                                        String essFlowId, ContractStatus status) {
            this(contractId, contractNo, essFlowId, status, null);
        }
    }
}
