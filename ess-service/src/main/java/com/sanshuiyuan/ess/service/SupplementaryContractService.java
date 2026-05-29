package com.sanshuiyuan.ess.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanshuiyuan.ess.config.EssProperties;
import com.sanshuiyuan.ess.domain.*;
import com.sanshuiyuan.ess.domain.SupplementaryContract.ContractType;
import com.sanshuiyuan.ess.domain.SupplementaryContract.SupplementaryStatus;
import com.sanshuiyuan.ess.exception.EssFlowException;
import com.sanshuiyuan.ess.infra.client.EssApiClient;
import com.sanshuiyuan.ess.infra.repository.ContractRepository;
import com.sanshuiyuan.ess.infra.repository.RefundContractLinkageRepository;
import com.sanshuiyuan.ess.infra.repository.SupplementaryContractRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.TreeMap;

/**
 * 补充协议服务。
 * <p>
 * 冷静期后退款须签署《设备退货与服务终止补充协议》。
 * 流程：生成 → 签署编排 → 归档。
 */
@Service
public class SupplementaryContractService {

    private static final Logger log = LoggerFactory.getLogger(SupplementaryContractService.class);

    private final SupplementaryContractRepository supplementaryContractRepository;
    private final RefundContractLinkageRepository linkageRepository;
    private final ContractRepository contractRepository;
    private final ContractNoGenerator contractNoGenerator;
    private final EssApiClient apiClient;
    private final EssProperties properties;
    private final EssApiLogService apiLogService;
    private final AuditTrailService auditTrailService;
    private final ObjectMapper objectMapper;

    public SupplementaryContractService(SupplementaryContractRepository supplementaryContractRepository,
                                         RefundContractLinkageRepository linkageRepository,
                                         ContractRepository contractRepository,
                                         ContractNoGenerator contractNoGenerator,
                                         EssApiClient apiClient,
                                         EssProperties properties,
                                         EssApiLogService apiLogService,
                                         AuditTrailService auditTrailService,
                                         ObjectMapper objectMapper) {
        this.supplementaryContractRepository = supplementaryContractRepository;
        this.linkageRepository = linkageRepository;
        this.contractRepository = contractRepository;
        this.contractNoGenerator = contractNoGenerator;
        this.apiClient = apiClient;
        this.properties = properties;
        this.apiLogService = apiLogService;
        this.auditTrailService = auditTrailService;
        this.objectMapper = objectMapper;
    }

    /**
     * 生成补充协议。
     * <p>
     * 1. 检查原合同存在性
     * 2. 生成补充协议编号
     * 3. 创建补充协议草稿
     * 4. 创建退款联动记录
     *
     * @param refundOrderId     退款订单 ID
     * @param originalContractId 原购机合同 ID
     * @param signerInfoJson    签署方信息
     * @param contractFieldsJson 合同字段
     * @return 补充协议
     */
    @Transactional
    public SupplementaryContract generateSupplementaryContract(String refundOrderId,
                                                                Long originalContractId,
                                                                String signerInfoJson,
                                                                String contractFieldsJson) {
        log.info("生成补充协议 [refundOrderId={}, originalContractId={}]",
                refundOrderId, originalContractId);

        // 幂等：检查是否已存在
        var existing = supplementaryContractRepository.findByRefundOrderId(refundOrderId);
        if (existing.isPresent()) {
            log.info("补充协议已存在，跳过创建 [refundOrderId={}, scId={}]",
                    refundOrderId, existing.get().getId());
            return existing.get();
        }

        // 校验原合同
        Contract originalContract = contractRepository.findById(originalContractId)
                .orElseThrow(() -> new IllegalArgumentException("原合同不存在: id=" + originalContractId));

        // 生成补充协议编号
        String contractNo = contractNoGenerator.generateContractNo("SC");

        // 创建补充协议草稿
        SupplementaryContract sc = SupplementaryContract.createDraft(
                originalContractId, contractNo, ContractType.DEVICE_RETURN,
                refundOrderId, signerInfoJson, contractFieldsJson);

        sc.markGenerated();
        sc = supplementaryContractRepository.save(sc);

        // 创建退款联动记录
        RefundContractLinkage linkage = RefundContractLinkage.create(sc.getId(), refundOrderId);
        linkageRepository.save(linkage);

        log.info("补充协议已生成 [scId={}, contractNo={}, refundOrderId={}]",
                sc.getId(), contractNo, refundOrderId);

        return sc;
    }

    /**
     * 发起补充协议签署。
     * <p>
     * 1. 调用腾讯电子签 CreateFlow + StartFlow
     * 2. 更新补充协议状态为 SIGNING
     *
     * @param scId 补充协议 ID
     * @return 签署结果
     */
    @Transactional
    public SupplementarySignResult initiateSigning(Long scId) {
        SupplementaryContract sc = supplementaryContractRepository.findById(scId)
                .orElseThrow(() -> new IllegalArgumentException("补充协议不存在: id=" + scId));

        if (sc.getStatus() != SupplementaryStatus.GENERATED) {
            throw new IllegalStateException(
                    String.format("补充协议状态不允许签署，当前状态: %s", sc.getStatus()));
        }

        log.info("发起补充协议签署 [scId={}, contractNo={}]", scId, sc.getContractNo());

        // 调用腾讯电子签创建签署流程
        String flowName = "三水元补充协议签署-" + sc.getContractNo();

        long start = System.currentTimeMillis();
        try {
            TreeMap<String, Object> params = new TreeMap<>();
            TreeMap<String, Object> operator = new TreeMap<>();
            operator.put("UserId", properties.operatorId());
            params.put("Operator", operator);
            params.put("FlowName", flowName);
            params.put("Approvers", sc.getSignerInfoJson() != null ? sc.getSignerInfoJson() : "[]");

            JsonNode response = apiClient.invoke("CreateFlow", params);
            String essFlowId = response.get("FlowId").asText();

            sc.startSigning(essFlowId);
            supplementaryContractRepository.save(sc);

            // 启动流程
            TreeMap<String, Object> startParams = new TreeMap<>();
            startParams.put("Operator", operator);
            startParams.put("FlowId", essFlowId);
            apiClient.invoke("StartFlow", startParams);

            int duration = (int) (System.currentTimeMillis() - start);
            apiLogService.recordSuccessAsync("CreateFlow+StartFlow",
                    params.toString(), response.toString(), 200, duration);

            // 审计事件
            auditTrailService.recordSystemEvent(sc.getOriginalContractId(),
                    ContractAuditTrail.Action.START_SIGN,
                    String.format("{\"supplementaryContractId\":%d,\"essFlowId\":\"%s\"}",
                            scId, essFlowId));

            log.info("补充协议签署已发起 [scId={}, essFlowId={}]", scId, essFlowId);

            return new SupplementarySignResult(scId, sc.getContractNo(), essFlowId, "SIGNING");

        } catch (EssFlowException e) {
            throw e;
        } catch (Exception e) {
            int duration = (int) (System.currentTimeMillis() - start);
            apiLogService.recordFailureAsync("CreateFlow", "{}", null, null, duration, e.getMessage());
            throw new RuntimeException("发起补充协议签署失败: " + e.getMessage(), e);
        }
    }

    /**
     * 补充协议签署完成回调处理。
     *
     * @param scId   补充协议 ID
     * @param pdfUrl PDF URL
     * @param pdfHash PDF 哈希
     */
    @Transactional
    public void completeSigning(Long scId, String pdfUrl, String pdfHash) {
        SupplementaryContract sc = supplementaryContractRepository.findById(scId)
                .orElseThrow(() -> new IllegalArgumentException("补充协议不存在: id=" + scId));

        sc.completeSigning(pdfUrl, pdfHash);
        supplementaryContractRepository.save(sc);

        // 更新联动状态为 SUPPLEMENTARY_SIGNED
        linkageRepository.findBySupplementaryContractId(scId).ifPresent(linkage -> {
            linkage.markSupplementarySigned();
            linkageRepository.save(linkage);
        });

        // 审计事件
        auditTrailService.recordSystemEvent(sc.getOriginalContractId(),
                ContractAuditTrail.Action.SIGN_COMPLETE,
                String.format("{\"supplementaryContractId\":%d,\"pdfHash\":\"%s\"}", scId, pdfHash));

        log.info("补充协议签署完成 [scId={}, contractNo={}]", scId, sc.getContractNo());
    }

    /**
     * 归档补充协议。
     */
    @Transactional
    public void archiveSupplementaryContract(Long scId) {
        SupplementaryContract sc = supplementaryContractRepository.findById(scId)
                .orElseThrow(() -> new IllegalArgumentException("补充协议不存在: id=" + scId));

        sc.archive();
        supplementaryContractRepository.save(sc);

        log.info("补充协议已归档 [scId={}, contractNo={}]", scId, sc.getContractNo());
    }

    /**
     * 查询补充协议状态。
     */
    @Transactional(readOnly = true)
    public SupplementaryContract getSupplementaryContract(Long scId) {
        return supplementaryContractRepository.findById(scId)
                .orElseThrow(() -> new IllegalArgumentException("补充协议不存在: id=" + scId));
    }

    /**
     * 根据退款订单查询补充协议。
     */
    @Transactional(readOnly = true)
    public SupplementaryContract getByRefundOrderId(String refundOrderId) {
        return supplementaryContractRepository.findByRefundOrderId(refundOrderId)
                .orElseThrow(() -> new IllegalArgumentException("退款订单无对应补充协议: " + refundOrderId));
    }

    /**
     * 签署结果。
     */
    public record SupplementarySignResult(
            Long scId,
            String contractNo,
            String essFlowId,
            String status
    ) {}
}
