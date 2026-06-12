package com.sanshuiyuan.ess.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanshuiyuan.ess.config.EssFileProperties;
import com.sanshuiyuan.ess.domain.Contract;
import com.sanshuiyuan.ess.domain.Contract.ContractStatus;
import com.sanshuiyuan.ess.domain.ContractAuditTrail;
import com.sanshuiyuan.ess.domain.ContractSnBinding;
import com.sanshuiyuan.ess.domain.EssFlowRecord;
import com.sanshuiyuan.ess.exception.EssFlowException;
import com.sanshuiyuan.ess.infra.repository.ContractRepository;
import com.sanshuiyuan.ess.infra.repository.ContractSnBindingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    private final ObjectMapper objectMapper;

    /**
     * 文件模式发起的协作者（均为可选注入）。
     * <p>三者齐备且 {@link EssFileProperties#enabled()} 为 true 时，走文件模式（渲染 PDF → CreateFlowByFiles）；
     * 否则维持既有模板模式，单元测试无需设置即保持旧行为。</p>
     */
    private EssFileProperties fileProperties;
    private ContractPdfRenderService pdfRenderService;
    private ContractGenerationService generationService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setFileProperties(EssFileProperties fileProperties) {
        this.fileProperties = fileProperties;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setPdfRenderService(ContractPdfRenderService pdfRenderService) {
        this.pdfRenderService = pdfRenderService;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setGenerationService(ContractGenerationService generationService) {
        this.generationService = generationService;
    }

    private boolean isFileModeEnabled() {
        return fileProperties != null
                && Boolean.TRUE.equals(fileProperties.enabled())
                && pdfRenderService != null
                && generationService != null;
    }

    public ContractSigningService(ContractRepository contractRepository,
                                   ContractSnBindingRepository snBindingRepository,
                                   EssContractService essContractService,
                                   ContractStateMachineService stateMachineService,
                                   ContractArchiveService archiveService,
                                   AuditTrailService auditTrailService,
                                   ObjectMapper objectMapper) {
        this.contractRepository = contractRepository;
        this.snBindingRepository = snBindingRepository;
        this.essContractService = essContractService;
        this.stateMachineService = stateMachineService;
        this.archiveService = archiveService;
        this.auditTrailService = auditTrailService;
        this.objectMapper = objectMapper;
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
        return initiateSigning(contractId, userId, null, null);
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
                                                    Contract.SignSource signSource,
                                                    String[] overrides) {
        return initiateSigning(contractId, userId, signSource, overrides, false);
    }

    /**
     * 发起签署流程（带签署来源 + 是否短信送达）。
     *
     * @param smsNotify true 时签署方 NotifyType=SMS，并在创建流程后立即 StartFlow，
     *                  由腾讯电子签给签署人手机下发带签署短链的短信（小程序认购走此路径，不跳转电子签小程序）。
     */
    @Transactional
    public SigningInitiationResult initiateSigning(Long contractId, Long userId,
                                                    Contract.SignSource signSource,
                                                    String[] overrides,
                                                    boolean smsNotify) {
        Contract contract = stateMachineService.getContract(contractId);

        // 校验状态
        if (!contract.getStatus().canTransitionTo(ContractStatus.SIGNING)) {
            throw new IllegalStateException(
                    String.format("合同当前状态为 %s，无法发起签署 [contractNo=%s]",
                            contract.getStatus(), contract.getContractNo()));
        }

        log.info("发起签署流程 [contractId={}, contractNo={}, userId={}, signSource={}, hasOverrides={}]",
                contractId, contract.getContractNo(), userId, signSource, overrides != null);

        // 调用腾讯电子签创建签署流程
        String contractNoStr = contract.getContractNo();
        String flowName = "三水元设备合同签署-" + contractNoStr;

        // 如果传入了真实身份信息，覆盖签署方 JSON 中的脱敏字段
        String signerJson = contract.getSignerInfoJson();
        if (overrides != null) {
            String phone = overrides[0];
            String realName = overrides.length > 1 ? overrides[1] : null;
            String idCard = overrides.length > 2 ? overrides[2] : null;
            if (phone != null && !phone.isBlank() && phone.matches("^1[3-9]\\d{9}$")) {
                signerJson = patchFieldInJson(signerJson, "phone", phone);
            }
            if (realName != null && !realName.isBlank()) {
                signerJson = patchFieldInJson(signerJson, "userName", realName);
            }
            if (idCard != null && !idCard.isBlank()) {
                signerJson = patchFieldInJson(signerJson, "idCardNo", idCard);
            }
            log.info("签署方信息已覆盖 [contractNo={}]", contract.getContractNo());
        }

        // 签署前验证：姓名必须为真实姓名，不能为脱敏值（含 *）或空
        validateRealName(signerJson, contract.getContractNo());

        EssFlowRecord flowRecord;
        if (isFileModeEnabled()) {
            // 文件模式：后端渲染「变量已填好」的 PDF → 上传 → CreateFlowByFiles（只放签名控件）。
            String markdown = generationService.getContractContent(contractId).mainContractContent();
            byte[] pdf = pdfRenderService.renderMarkdownToPdf(markdown, flowName);
            flowRecord = essContractService.createFlowByFiles(
                    contractNoStr, flowName, signerJson, pdf, contractNoStr + ".pdf", smsNotify);
            log.info("文件模式发起签署 [contractNo={}, pdfBytes={}]", contractNoStr, pdf.length);
        } else {
            // 模板模式（既有行为）：变量交给腾讯电子签模板控件。
            flowRecord = essContractService.createFlow(contractNoStr, flowName, signerJson, smsNotify);
        }

        // 短信送达：CreateFlow / CreateFlowByFiles 创建的流程本账号下即「已发起」(自动 StartFlow)，
        // NotifyType=SMS 会在流程发起时由腾讯电子签直接给签署人下发带签署短链的短信——无需、也不能再显式调
        // StartFlow（会撞 OperationDenied.FlowHasStarted，且 EssApiClient 重试该错误会拖垮 cend 读超时致 500）。
        // 既有 H5/跳转路径同样从不显式 StartFlow，与此一致。

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

        // 自动触发归档——必须在 SIGNED 事务【提交后】执行。
        // archiveContract 是 @Transactional 且失败会抛异常：若在本事务内调用，异常会把事务标记成
        // rollback-only，连同刚写入的 SIGNED 一起回滚（即使这里 catch 也无效），导致签署完成被整体回滚
        // ——线上表现为「签完合同小程序仍显示尚未完成签约」。归档失败已有 markPendingArchive + 归档重试兜底，
        // 绝不能反向回滚签署完成状态。故放到 afterCommit 中以独立事务执行。
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    tryAutoArchive(contractId);
                }
            });
        } else {
            tryAutoArchive(contractId);
        }
    }

    /**
     * 归档尝试（best-effort）：失败仅记录日志，由 {@code markPendingArchive} + 归档重试任务兜底，
     * 不向上抛出、不影响已完成的签署状态。
     */
    private void tryAutoArchive(Long contractId) {
        try {
            archiveService.archiveContract(contractId);
        } catch (Exception e) {
            log.error("签署后自动归档失败，将等待重试 [contractId={}]: {}", contractId, e.getMessage());
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

    /**
     * 在签署方信息 JSON 中覆盖指定字段。
     * 用于修复旧合同脱敏数据：前端传真实身份信息 → 签署时动态覆盖。
     */
    static String patchFieldInJson(String json, String fieldName, String value) {
        String escapedVal = value.replace("\\", "\\\\").replace("\"", "\\\"");
        if (json.contains("\"" + fieldName + "\"")) {
            return json.replaceAll("\"" + fieldName + "\"\\s*:\\s*\"[^\"]*\"",
                    "\"" + fieldName + "\":\"" + escapedVal + "\"");
        }
        return json.replaceFirst("}$", ",\"" + fieldName + "\":\"" + escapedVal + "\"}");
    }

    /**
     * 签署前验证：签署方姓名必须为真实姓名，不能为脱敏值（含 *）或空。
     * <p>
     * 腾讯电子签 CreateFlow 要求 ApproverName 为身份证件上的真实姓名，
     * 脱敏姓名（如"张**"）或空值会导致 InvalidParameter.Name 错误。
     */
    private void validateRealName(String signerJson, String contractNo) {
        try {
            JsonNode root = objectMapper.readTree(signerJson);
            JsonNode signers = root.isArray() ? root : objectMapper.createArrayNode().add(root);
            for (JsonNode signer : signers) {
                String name = signer.has("userName") ? signer.get("userName").asText() : "";
                if (name.isBlank()) {
                    throw new EssFlowException(contractNo, "签署方姓名为空，请重新完成实名认证后再试");
                }
                if (name.contains("*")) {
                    throw new EssFlowException(contractNo,
                            "签署方姓名为脱敏值（\"" + name + "\"），请重新完成实名认证获取真实姓名后再试");
                }
                // 姓名至少 2 个中文字符
                if (!name.matches("[\\u4e00-\\u9fa5·]{2,}")) {
                    throw new EssFlowException(contractNo,
                            "签署方姓名格式不正确（\"" + name + "\"），需为真实中文姓名");
                }
            }
        } catch (EssFlowException e) {
            throw e;
        } catch (Exception e) {
            log.warn("解析签署方姓名验证失败 [contractNo={}]: {}", contractNo, e.getMessage());
            throw new EssFlowException(contractNo, "签署方信息解析失败，请重新生成合同");
        }
    }
}
