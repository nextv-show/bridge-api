package com.sanshuiyuan.ess.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanshuiyuan.ess.config.ContractTemplateDataInitializer;
import com.sanshuiyuan.ess.domain.Contract;
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
 * 读取 KYC + SKU 数据 → 填充模板 → 生成合同实例。
 * 将《设备购买协议》《设备代运营服务协议》合并为一份主合同，
 * 《产权归属确认书》作为附件。
 */
@Service
public class ContractGenerationService {

    private static final Logger log = LoggerFactory.getLogger(ContractGenerationService.class);

    private final ContractTemplateService templateService;
    private final ContractNoGenerator contractNoGenerator;
    private final ContractRepository contractRepository;
    private final ContractSnBindingRepository snBindingRepository;
    private final ObjectMapper objectMapper;

    public ContractGenerationService(ContractTemplateService templateService,
                                      ContractNoGenerator contractNoGenerator,
                                      ContractRepository contractRepository,
                                      ContractSnBindingRepository snBindingRepository,
                                      ObjectMapper objectMapper) {
        this.templateService = templateService;
        this.contractNoGenerator = contractNoGenerator;
        this.contractRepository = contractRepository;
        this.snBindingRepository = snBindingRepository;
        this.objectMapper = objectMapper;
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
            String phone
    ) {}

    /**
     * 生成合同结果。
     */
    public record GenerateContractResult(
            Long contractId,
            String contractNo,
            String mainContractContent,
            String attachmentContent,
            Contract.ContractStatus status
    ) {}

    /**
     * 生成合同。
     * <p>
     * 1. 获取最新主合同模板
     * 2. 填充用户实名信息、设备信息、SN预占位
     * 3. 同时生成附件（产权归属确认书）
     * 4. 创建合同实例 + SN绑定
     */
    @Transactional
    public GenerateContractResult generateContract(GenerateContractRequest request) {
        // 1. 获取模板
        ContractTemplate mainTemplate = templateService.getLatestVersion(
                ContractTemplateDataInitializer.MAIN_CONTRACT_CODE);
        ContractTemplate attachTemplate = templateService.getLatestVersion(
                ContractTemplateDataInitializer.PROPERTY_CERT_CODE);

        // 2. 生成合同编号
        String contractNo = contractNoGenerator.generate();

        // 3. 创建合同草稿
        Contract contract = Contract.createDraft(
                contractNo, mainTemplate.getId(), request.userId(),
                request.orderId(), request.deviceSn());
        contract = contractRepository.save(contract);

        // 4. 构建填充字段
        Map<String, String> fields = buildFields(request, contractNo);
        String fieldsJson = toJson(fields);

        // 5. 填充主合同
        String mainContent = fillTemplate(mainTemplate.getContentBody(), fields);

        // 6. 填充附件（产权归属确认书）
        String attachContent = fillTemplate(attachTemplate.getContentBody(), fields);

        // 7. 构建签署方信息
        Map<String, Object> signerInfo = buildSignerInfo(request);
        String signerInfoJson = toJson(signerInfo);

        // 8. 标记为已生成
        contract.markGenerated(fieldsJson, signerInfoJson);
        contractRepository.save(contract);

        // 9. 建立 SN 预占位绑定
        createSnBinding(contract.getId(), request.deviceSn());

        log.info("合同已生成 [contractNo={}, userId={}, deviceSn={}]",
                contractNo, request.userId(), request.deviceSn());

        return new GenerateContractResult(
                contract.getId(), contractNo, mainContent, attachContent, contract.getStatus());
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

        // 获取附件模板
        ContractTemplate attachTemplate = templateService.getLatestVersion(
                ContractTemplateDataInitializer.PROPERTY_CERT_CODE);
        String attachContent = fillTemplate(attachTemplate.getContentBody(), fields);

        return new GenerateContractResult(
                contract.getId(), contract.getContractNo(),
                mainContent, attachContent, contract.getStatus());
    }

    private Map<String, String> buildFields(GenerateContractRequest request, String contractNo) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("contractNo", contractNo);
        fields.put("signDate", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")));
        fields.put("userName", request.userName());
        fields.put("idCardNo", request.idCardNo());
        fields.put("phone", request.phone());
        fields.put("deviceModel", request.deviceModel());
        fields.put("deviceSn", request.deviceSn() != null ? request.deviceSn() : "待分配");
        fields.put("devicePrice", request.devicePrice());
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
