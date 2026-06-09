package com.sanshuiyuan.ess.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanshuiyuan.ess.config.EssFileProperties;
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
    /**
     * Contract 状态机桥接（可选注入）。
     * <p>
     * - 生产环境总是注入：FlowRecord COMPLETED 时同步把 Contract 推到 SIGNED；
     * - 单元测试不依赖 Contract 域，bridge 缺省为 null，逻辑会安全跳过。
     * 使用 setter 注入避免与 ContractSigningService 形成构造循环。
     */
    private ContractCompletionBridge completionBridge;
    /**
     * 文件模式发起配置（可选注入）。
     * <p>缺省为 null 时，{@link #createFlowByFiles} 不可用；既有模板模式 {@link #createFlow} 不受影响。</p>
     */
    private EssFileProperties fileProperties;

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

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setCompletionBridge(ContractCompletionBridge completionBridge) {
        this.completionBridge = completionBridge;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setFileProperties(EssFileProperties fileProperties) {
        this.fileProperties = fileProperties;
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

    /**
     * 文件模式创建签署流程（CreateFlowByFiles）。
     * <p>
     * 由调用方先把合同正文渲染成「变量已填好」的 PDF：
     * <ol>
     *   <li>UploadFiles 上传 PDF → 取得 FileId（走文件专用域名）；</li>
     *   <li>CreateFlowByFiles 用 FileId + Approvers（含关键字定位的签名/签章控件）创建流程。</li>
     * </ol>
     * 业务变量不再交给腾讯电子签，平台只负责放置签名/签章控件。
     *
     * @param contractId  业务合同号
     * @param flowName    流程名
     * @param signersJson 签署方业务 JSON
     * @param pdfBytes    已渲染好的合同 PDF
     * @param fileName    PDF 文件名（如 {@code CT-xxx.pdf}）
     */
    @Transactional
    public EssFlowRecord createFlowByFiles(String contractId, String flowName, String signersJson,
                                            byte[] pdfBytes, String fileName) {
        EssFileProperties fp = requireFileProperties();
        flowRecordRepository.findByContractId(contractId).ifPresent(existing -> {
            throw new EssFlowException(contractId, "合同已存在签署流程，flowId=" + existing.getEssFlowId());
        });

        EssFlowRecord record = EssFlowRecord.create(contractId, signersJson);
        flowRecordRepository.save(record);

        long start = System.currentTimeMillis();
        try {
            String fileId = uploadFile(pdfBytes, fileName, fp);

            TreeMap<String, Object> params = new TreeMap<>();
            params.put("Operator", buildOperator());
            params.put("FlowName", flowName);
            params.put("Unordered", false);
            params.put("FileIds", java.util.List.of(fileId));
            params.put("Approvers", buildFileApprovers(signersJson, fp));

            JsonNode response = apiClient.invoke("CreateFlowByFiles", params);
            String flowId = response.get("FlowId").asText();

            record.assignFlowId(flowId);
            flowRecordRepository.save(record);

            int duration = (int) (System.currentTimeMillis() - start);
            apiLogService.recordSuccessAsync("CreateFlowByFiles", params.toString(), response.toString(), 200, duration);

            log.info("文件模式签署流程已创建 [contractId={}, flowId={}, fileId={}]", contractId, flowId, fileId);
            return record;

        } catch (EssFlowException e) {
            throw e;
        } catch (Exception e) {
            int duration = (int) (System.currentTimeMillis() - start);
            apiLogService.recordFailureAsync("CreateFlowByFiles", "{}", null, null, duration, e.getMessage());
            throw new EssFlowException(contractId, "文件模式创建签署流程失败: " + e.getMessage());
        }
    }

    /**
     * 上传 PDF 至腾讯电子签文件专用域名，返回 FileId。
     * UploadFiles 用 JSON + base64 传输文件内容。
     */
    private String uploadFile(byte[] pdfBytes, String fileName, EssFileProperties fp) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new EssFlowException(fileName, "待上传的合同 PDF 为空");
        }
        String base64 = java.util.Base64.getEncoder().encodeToString(pdfBytes);

        TreeMap<String, Object> params = new TreeMap<>();
        params.put("BusinessType", "DOCUMENT");
        params.put("FileType", "PDF");
        TreeMap<String, Object> fileInfo = new TreeMap<>();
        fileInfo.put("FileName", fileName);
        fileInfo.put("FileBody", base64);
        params.put("FileInfos", java.util.List.of(fileInfo));

        long start = System.currentTimeMillis();
        // 注意：日志参数不落 base64 文件体，避免巨大日志/敏感内容。
        JsonNode response = apiClient.invoke("UploadFiles", params, fp.endpoint());
        int duration = (int) (System.currentTimeMillis() - start);
        apiLogService.recordSuccessAsync("UploadFiles",
                "{\"FileName\":\"" + fileName + "\",\"bytes\":" + pdfBytes.length + "}",
                response.toString(), 200, duration);

        JsonNode fileIds = response.get("FileIds");
        if (fileIds == null || !fileIds.isArray() || fileIds.isEmpty()) {
            throw new EssFlowException(fileName, "UploadFiles 未返回 FileIds");
        }
        return fileIds.get(0).asText();
    }

    private EssFileProperties requireFileProperties() {
        if (fileProperties == null) {
            throw new IllegalStateException("文件模式未配置（EssFileProperties 缺失），无法调用 createFlowByFiles");
        }
        return fileProperties;
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

            // 腾讯 ESS 实际响应结构：
            //   { "FlowDetailInfos": [ { "FlowId": "...", "FlowStatus": 2, ... } ] }
            // 历史代码错误地读 response.FlowInfo.Status（字段不存在），导致同步永远 no-op。
            // 兼容老 mock 路径：若 FlowDetailInfos 不存在，回退到 FlowInfo.Status。
            String status = extractFlowStatus(response);

            int duration = (int) (System.currentTimeMillis() - start);
            apiLogService.recordSuccessAsync("DescribeFlowInfo", params.toString(),
                    response.toString(), 200, duration);

            FlowStatus mapped = EssStatusMapper.map(status);
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

    /**
     * 从 ESS DescribeFlowInfo 响应中抽取流程状态字符串。
     * <p>
     * 优先读新版 {@code FlowDetailInfos[0].FlowStatus}；兼容老版 {@code FlowInfo.Status}。
     * 返回 null 表示响应中没有任何可识别的状态字段。
     */
    private static String extractFlowStatus(JsonNode response) {
        if (response == null) return null;
        if (response.has("FlowDetailInfos")
                && response.get("FlowDetailInfos").isArray()
                && response.get("FlowDetailInfos").size() > 0) {
            JsonNode first = response.get("FlowDetailInfos").get(0);
            if (first.has("FlowStatus")) {
                return first.get("FlowStatus").asText();
            }
        }
        if (response.has("FlowInfo") && response.get("FlowInfo").has("Status")) {
            return response.get("FlowInfo").get("Status").asText();
        }
        return null;
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
                    String phone = signer.get("phone").asText();
                    // 仅当手机号为有效大陆手机号时才传入 ESS API，防止脱敏/空值导致腾讯电子签报错
                    if (phone != null && phone.matches("^1[3-9]\\d{9}$")) {
                        approver.put("ApproverMobile", phone);
                    } else {
                        log.warn("签署方手机号无效，跳过 ApproverMobile [phone={}]", phone);
                    }
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

    /**
     * 文件模式 Approvers：在个人签署方上挂关键字定位的签名/签署日期控件，
     * 并按配置可选追加乙方企业签章方。
     */
    private java.util.List<TreeMap<String, Object>> buildFileApprovers(String signerInfoJson, EssFileProperties fp) {
        java.util.List<TreeMap<String, Object>> approvers = buildApprovers(signerInfoJson);
        for (TreeMap<String, Object> approver : approvers) {
            java.util.List<TreeMap<String, Object>> signComponents = new java.util.ArrayList<>();
            // 甲方签名（及可选签署日期）：用签名一组定位参数。
            signComponents.add(buildSignComponent("SIGN_SIGNATURE", fp.signatureKeyword(),
                    fp.signWidth(), fp.signHeight(), fp.relativeLocation(), fp.offsetX(), fp.offsetY()));
            if (fp.dateKeyword() != null && !fp.dateKeyword().isBlank()) {
                signComponents.add(buildSignComponent("SIGN_DATE", fp.dateKeyword(),
                        fp.signWidth(), fp.signHeight(), fp.relativeLocation(), fp.offsetX(), fp.offsetY()));
            }
            approver.put("SignComponents", signComponents);
        }

        // 可选：乙方企业签章方（静默签需腾讯电子签账号侧授权印章，默认关闭）。
        // 印章靠近页边，用独立一组定位参数（默认 Below），避免 Right 溢出页面。
        if (Boolean.TRUE.equals(fp.companySeal())) {
            TreeMap<String, Object> company = new TreeMap<>();
            company.put("ApproverType", 0); // 0=企业
            company.put("OrganizationName", fp.companyName());
            company.put("ApproverName", fp.companyName());
            company.put("RecipientId", properties.operatorId());
            company.put("NotifyType", "NONE");
            company.put("SignComponents", java.util.List.of(
                    buildSignComponent("SIGN_SEAL", fp.sealKeyword(),
                            fp.sealWidth(), fp.sealHeight(), fp.sealRelativeLocation(),
                            fp.sealOffsetX(), fp.sealOffsetY())));
            approvers.add(company);
        }
        return approvers;
    }

    /**
     * 构造关键字定位的签名/签章控件。
     * <p>关键字定位（GenerateMode=KEYWORD）下，关键字字符串通过 {@code ComponentId} 传入，
     * 控件放在该关键字在 PDF 中的相对方位（{@code RelativeLocation}）。
     * 尺寸/方位/偏移由调用方按签名 or 签章分别传入。</p>
     */
    private TreeMap<String, Object> buildSignComponent(String type, String keyword,
                                                        double width, double height,
                                                        String relativeLocation,
                                                        double offsetX, double offsetY) {
        TreeMap<String, Object> c = new TreeMap<>();
        c.put("ComponentType", type);
        c.put("ComponentName", type + "-" + keyword);
        c.put("ComponentId", keyword);
        c.put("GenerateMode", "KEYWORD");
        c.put("FileIndex", 0);
        c.put("ComponentWidth", width);
        c.put("ComponentHeight", height);
        c.put("RelativeLocation", relativeLocation);
        c.put("OffsetX", offsetX);
        c.put("OffsetY", offsetY);
        c.put("KeywordOrder", "Positive");
        return c;
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

        // 关键桥接：远端首次报告 COMPLETED 时，必须把 Contract 状态机也推进到 SIGNED，
        // 否则 H5 /contracts/{id}/status 永远轮询不到完成状态。
        // 历史 bug：长期只更新 EssFlowRecord 不更新 Contract，导致用户签完仍在转圈。
        if (status == FlowStatus.COMPLETED && completionBridge != null) {
            try {
                completionBridge.bridgeToSigned(record.getContractId(), null);
            } catch (Exception e) {
                log.warn("FlowRecord COMPLETED 后桥接 Contract 失败 [contractNo={}]: {}",
                        record.getContractId(), e.getMessage());
                // 桥接失败不影响 FlowRecord 状态保存；兜底 Job 会重试。
            }
        }
    }
}
