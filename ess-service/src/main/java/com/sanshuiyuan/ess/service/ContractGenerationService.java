package com.sanshuiyuan.ess.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanshuiyuan.ess.config.ContractTemplateDataInitializer;
import com.sanshuiyuan.ess.domain.Contract;
import com.sanshuiyuan.ess.domain.ContractAuditTrail;
import com.sanshuiyuan.ess.domain.ContractTemplate;
import com.sanshuiyuan.ess.infra.repository.ContractRepository;
import com.sanshuiyuan.ess.infra.repository.ContractSnBindingRepository;
import com.sanshuiyuan.ess.domain.ContractSnBinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 合同生成服务。
 * <p>
 * 读取 KYC + SKU 数据 → 填充统一合同模板 → 生成合同实例。
 * 三合一统一合同模板包含：设备买卖、物权归属、代运营及冷静期退款规则。
 */
@Service
public class ContractGenerationService {

    private static final Logger log = LoggerFactory.getLogger(ContractGenerationService.class);

    private final ContractTemplateService templateService;
    private final ContractNoGenerator contractNoGenerator;
    private final ContractRepository contractRepository;
    private final ContractSnBindingRepository snBindingRepository;
    private final ObjectMapper objectMapper;
    private final AuditTrailService auditTrailService;

    public ContractGenerationService(ContractTemplateService templateService,
                                      ContractNoGenerator contractNoGenerator,
                                      ContractRepository contractRepository,
                                      ContractSnBindingRepository snBindingRepository,
                                      ObjectMapper objectMapper,
                                      AuditTrailService auditTrailService) {
        this.templateService = templateService;
        this.contractNoGenerator = contractNoGenerator;
        this.contractRepository = contractRepository;
        this.snBindingRepository = snBindingRepository;
        this.objectMapper = objectMapper;
        this.auditTrailService = auditTrailService;
    }

    /**
     * 合同用途。决定选用哪套模板与签署流程文案，彼此完全隔离。
     */
    public enum ContractPurpose {
        /** 设备认购主合同（统一三合一），编码 {@link ContractTemplateDataInitializer#MAIN_CONTRACT_CODE}。 */
        MAIN_CONTRACT,
        /** 实名认证 / 用水需求发布承诺书（spec 107），编码 {@link ContractTemplateDataInitializer#KYC_AUTH_CONTRACT_CODE}。 */
        KYC_AUTH;

        /** 解析用途，空 / 未知一律回退到设备认购主合同（保持既有行为）。 */
        public static ContractPurpose parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return MAIN_CONTRACT;
            }
            try {
                return ContractPurpose.valueOf(raw.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return MAIN_CONTRACT;
            }
        }

        /** 用途对应的模板编码。 */
        public String templateCode() {
            return this == KYC_AUTH
                    ? ContractTemplateDataInitializer.KYC_AUTH_CONTRACT_CODE
                    : ContractTemplateDataInitializer.MAIN_CONTRACT_CODE;
        }
    }

    /**
     * 生成合同请求参数。
     */
    public record GenerateContractRequest(
            Long userId,
            String orderId,
            String deviceSn,
            String deviceModel,
            String devicePrice,
            String userName,
            String idCardNo,
            String phone,
            ContractPurpose contractPurpose
    ) {
        public GenerateContractRequest {
            if (contractPurpose == null) {
                contractPurpose = ContractPurpose.MAIN_CONTRACT;
            }
        }

        /** 兼容旧调用方（无 contractPurpose）：默认设备认购主合同。 */
        public GenerateContractRequest(Long userId, String orderId, String deviceSn,
                                       String deviceModel, String devicePrice,
                                       String userName, String idCardNo, String phone) {
            this(userId, orderId, deviceSn, deviceModel, devicePrice,
                    userName, idCardNo, phone, ContractPurpose.MAIN_CONTRACT);
        }
    }

    /**
     * 生成合同结果。
     */
    public record GenerateContractResult(
            Long contractId,
            String contractNo,
            String mainContractContent,
            @Deprecated
            String attachmentContent,
            Contract.ContractStatus status
    ) {
        /**
         * @deprecated 三合一统一合同模板不再生成独立附件，始终返回 null。
         */
        @Deprecated
        public String attachmentContent() {
            return null;
        }
    }

    /**
     * 生成合同。
     * <p>
     * 1. 获取最新统一合同模板
     * 2. 填充用户实名信息、设备信息、SN预占位
     * 3. 创建合同实例 + SN绑定
     */
    @Transactional
    public GenerateContractResult generateContract(GenerateContractRequest request) {
        boolean isKycAuth = request.contractPurpose() == ContractPurpose.KYC_AUTH;
        // 服务边界强制隔离：KYC_AUTH 实名承诺书与设备认购合同互不复用。即便调用方绕过 cend 直接
        // 注入 deviceSn/deviceModel/devicePrice/orderId，也一律清空，杜绝借承诺书合同预占任意 SN。
        GenerateContractRequest req = isKycAuth ? sanitizeForKycAuth(request) : request;

        // 1. 按用途选择模板：设备认购走 MAIN_CONTRACT；实名承诺走 KYC_AUTH_CONTRACT（彼此隔离）。
        ContractTemplate mainTemplate = templateService.getLatestVersion(
                req.contractPurpose().templateCode());

        // 2. 生成合同编号
        String contractNo = contractNoGenerator.generate();

        // 3. 创建合同草稿
        Contract contract = Contract.createDraft(
                contractNo, mainTemplate.getId(), req.userId(),
                req.orderId(), req.deviceSn());
        contract = contractRepository.save(contract);

        // 4. 构建填充字段
        Map<String, String> fields = buildFields(req, contractNo);
        String fieldsJson = toJson(fields);

        // 5. 填充统一合同内容
        String mainContent = fillTemplate(mainTemplate.getContentBody(), fields);

        // 6. 构建签署方信息
        Map<String, Object> signerInfo = buildSignerInfo(req);
        String signerInfoJson = toJson(signerInfo);

        // 7. 标记为已生成
        contract.markGenerated(fieldsJson, signerInfoJson);
        contractRepository.save(contract);

        // 8. 建立 SN 预占位绑定（防御性：KYC_AUTH 承诺书不涉及设备，绝不建 SN 绑定）
        if (req.contractPurpose() != ContractPurpose.KYC_AUTH) {
            createSnBinding(contract.getId(), req.deviceSn());
        }

        // 9. 审计事件：合同创建 + 生成
        auditTrailService.recordUserEvent(contract.getId(),
                ContractAuditTrail.Action.CREATE, req.userId(),
                String.format("{\"orderId\":\"%s\",\"deviceSn\":\"%s\"}",
                        req.orderId(), req.deviceSn()), null);
        auditTrailService.recordSystemEvent(contract.getId(),
                ContractAuditTrail.Action.GENERATE,
                String.format("{\"templateId\":%d,\"contractNo\":\"%s\"}",
                        mainTemplate.getId(), contractNo));

        log.info("合同已生成 [contractNo={}, userId={}, deviceSn={}]",
                contractNo, req.userId(), req.deviceSn());

        return new GenerateContractResult(
                contract.getId(), contractNo, mainContent,
                null, // attachmentContent 已弃用，始终返回 null
                contract.getStatus());
    }

    /**
     * 根据合同 ID 获取已生成的合同内容。
     */
    @Transactional(readOnly = true)
    public GenerateContractResult getContractContent(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("合同不存在: id=" + contractId));

        // 重新填充模板以获取最新内容
        Map<String, String> fields = fromJson(contract.getContractFieldsJson());

        ContractTemplate mainTemplate = templateService.getById(contract.getTemplateId());
        String mainContent = fillTemplate(mainTemplate.getContentBody(), fields);

        return new GenerateContractResult(
                contract.getId(), contract.getContractNo(),
                mainContent,
                null, // attachmentContent 已弃用，始终返回 null
                contract.getStatus());
    }

    /**
     * KYC_AUTH 强隔离：剥离一切设备/订单字段，保留实名身份字段。
     * 防止请求方注入 deviceSn/deviceModel/devicePrice/orderId 污染承诺书合同草稿与字段 JSON。
     */
    private static GenerateContractRequest sanitizeForKycAuth(GenerateContractRequest request) {
        return new GenerateContractRequest(
                request.userId(), "", null, "", "",
                request.userName(), request.idCardNo(), request.phone(),
                ContractPurpose.KYC_AUTH);
    }

    private Map<String, String> buildFields(GenerateContractRequest request, String contractNo) {
        // 全部用空串兜底：实名承诺书（KYC_AUTH）不带设备字段，fillTemplate 对 null 替换值会 NPE。
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("contractNo", contractNo);
        fields.put("signDate", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")));
        fields.put("userName", nullToEmpty(request.userName()));
        fields.put("idCardNo", nullToEmpty(request.idCardNo()));
        fields.put("phone", nullToEmpty(request.phone()));
        fields.put("deviceModel", nullToEmpty(request.deviceModel()));
        fields.put("deviceSn", request.deviceSn() != null ? request.deviceSn() : "待分配");
        fields.put("devicePrice", nullToEmpty(request.devicePrice()));
        fields.put("legalRepresentative", "");
        fields.put("companyAddress", "");
        return fields;
    }

    private Map<String, Object> buildSignerInfo(GenerateContractRequest request) {
        Map<String, Object> signer = new LinkedHashMap<>();
        signer.put("userId", request.userId());
        signer.put("userName", request.userName());
        signer.put("idCardNo", request.idCardNo());
        signer.put("phone", request.phone());
        signer.put("role", "PURCHASER");
        return signer;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private String fillTemplate(String template, Map<String, String> fields) {
        String result = template;
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    private void createSnBinding(Long contractId, String deviceSn) {
        if (deviceSn != null && !deviceSn.isBlank()) {
            ContractSnBinding binding = ContractSnBinding.preAllocate(contractId, deviceSn);
            snBindingRepository.save(binding);
            log.info("SN预占位绑定已创建 [contractId={}, deviceSn={}]", contractId, deviceSn);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> fromJson(String json) {
        try {
            return objectMapper.readValue(json, LinkedHashMap.class);
        } catch (Exception e) {
            throw new IllegalStateException("解析合同字段 JSON 失败: " + e.getMessage());
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new IllegalStateException("序列化失败: " + e.getMessage());
        }
    }
}
