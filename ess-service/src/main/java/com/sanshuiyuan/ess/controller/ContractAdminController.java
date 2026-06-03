package com.sanshuiyuan.ess.controller;

import com.sanshuiyuan.ess.domain.Contract;
import com.sanshuiyuan.ess.domain.ContractAccessLog;
import com.sanshuiyuan.ess.domain.ContractAuditTrail;
import com.sanshuiyuan.ess.service.AuditTrailService;
import com.sanshuiyuan.ess.service.CertificateService;
import com.sanshuiyuan.ess.service.ContractAccessLogService;
import com.sanshuiyuan.ess.service.ContractArchiveService;
import com.sanshuiyuan.ess.service.ContractQueryService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理后台合同管理控制器。
 * <p>
 * T20.9:  GET /api/admin/contracts — 合同列表（多维度检索）
 * T20.10: GET /api/admin/contracts/{id} — 合同详情（完整审计信息）
 * T20.11: GET /api/admin/contracts/{id}/audit — 合同审计记录查询
 * T22.8:  GET /api/admin/contracts/certificate/{contractId} — 出证信息查询
 * T22.9:  GET /api/admin/contracts/certificate/{contractId}/download — 出证 PDF 下载
 * T22.10: GET /api/admin/contracts/search — 增强检索
 * T22.11: GET /api/admin/contracts/{id}/audit-trail — 审计轨迹查询
 */
@RestController
@RequestMapping("/api/admin/contracts")
public class ContractAdminController {

    private static final Logger log = LoggerFactory.getLogger(ContractAdminController.class);
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ContractQueryService queryService;
    private final ContractAccessLogService accessLogService;
    private final ContractArchiveService archiveService;
    private final CertificateService certificateService;
    private final AuditTrailService auditTrailService;
    private final com.sanshuiyuan.ess.service.ReconcileSigningContractsJob reconcileJob;
    private final com.sanshuiyuan.ess.service.ContractCompletionBridge completionBridge;
    private final com.sanshuiyuan.ess.service.EssContractService essContractService;
    private final com.sanshuiyuan.ess.infra.repository.ContractRepository contractRepository;

    public ContractAdminController(ContractQueryService queryService,
                                    ContractAccessLogService accessLogService,
                                    ContractArchiveService archiveService,
                                    CertificateService certificateService,
                                    AuditTrailService auditTrailService,
                                    com.sanshuiyuan.ess.service.ReconcileSigningContractsJob reconcileJob,
                                    com.sanshuiyuan.ess.service.ContractCompletionBridge completionBridge,
                                    com.sanshuiyuan.ess.service.EssContractService essContractService,
                                    com.sanshuiyuan.ess.infra.repository.ContractRepository contractRepository) {
        this.queryService = queryService;
        this.accessLogService = accessLogService;
        this.archiveService = archiveService;
        this.certificateService = certificateService;
        this.auditTrailService = auditTrailService;
        this.reconcileJob = reconcileJob;
        this.completionBridge = completionBridge;
        this.essContractService = essContractService;
        this.contractRepository = contractRepository;
    }

    // ========== 手动主动查单（运维兜底） ==========

    /**
     * 全量重放：扫描所有 SIGNING 合同，主动查询 ESS 并把已完成的推进到 SIGNED。
     * 用于历史漂移合同的一次性修复。
     */
    @PostMapping("/reconcile-signing")
    public ResponseEntity<Map<String, Object>> reconcileSigning() {
        log.info("管理后台手动触发 SIGNING 合同主动查单兜底");
        int scanned = reconcileJob.runOnce();
        return ResponseEntity.ok(Map.of(
                "code", 0,
                "scanned", scanned,
                "message", "已触发 SIGNING 合同主动查单兜底"
        ));
    }

    /**
     * 单合同强制重放：对指定合同主动查 ESS + 桥接。
     * 用于个案排障（例如客服反馈用户卡在签署中）。
     */
    @PostMapping("/{id}/reconcile-signing")
    public ResponseEntity<Map<String, Object>> reconcileOne(@PathVariable Long id) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("合同不存在: id=" + id));
        log.info("管理后台手动触发单合同主动查单 [contractNo={}]", contract.getContractNo());

        Map<String, Object> resp = new HashMap<>();
        resp.put("contractId", id);
        resp.put("contractNo", contract.getContractNo());
        resp.put("statusBefore", contract.getStatus().name());

        if (contract.getEssFlowId() == null) {
            resp.put("code", -1);
            resp.put("message", "合同尚未发起 ESS 流程");
            return ResponseEntity.ok(resp);
        }

        try {
            essContractService.describeFlowStatus(contract.getContractNo());
        } catch (Exception e) {
            log.warn("describeFlowStatus 失败 [contractNo={}]: {}",
                    contract.getContractNo(), e.getMessage());
        }
        boolean bridged = completionBridge.bridgeToSigned(contract.getContractNo(), null);

        Contract reloaded = contractRepository.findById(id).orElseThrow();
        resp.put("code", 0);
        resp.put("bridged", bridged);
        resp.put("statusAfter", reloaded.getStatus().name());
        resp.put("message", bridged ? "已推进到 SIGNED" : "无需推进（远端仍未完成或已是最终态）");
        return ResponseEntity.ok(resp);
    }

    // ========== 失败重试：归档 / 出证（spec 006 Phase E） ==========

    /**
     * 手动重试归档（archiveStatus=FAILED 的存量处理）。
     */
    @PostMapping("/{id}/retry-archive")
    public ResponseEntity<Map<String, Object>> retryArchive(@PathVariable Long id) {
        log.info("管理后台手动重试归档 [contractId={}]", id);
        Map<String, Object> resp = new HashMap<>();
        resp.put("contractId", id);
        try {
            ContractArchiveService.ArchiveResult r = archiveService.archiveContract(id);
            resp.put("code", r.success() ? 0 : -1);
            resp.put("contractNo", r.contractNo());
            resp.put("success", r.success());
            resp.put("message", r.message());
        } catch (Exception e) {
            // archiveContract 失败会抛 RuntimeException；统一捕获为 200 + {code:-1}，便于 BFF 透传错误。
            log.warn("重试归档失败 [contractId={}]: {}", id, e.getMessage());
            resp.put("code", -1);
            resp.put("success", false);
            resp.put("message", "归档重试失败：" + e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }

    /**
     * 手动重试出证（certificateStatus=FAILED 的存量处理）。
     */
    @PostMapping("/{id}/retry-certificate")
    public ResponseEntity<Map<String, Object>> retryCertificate(@PathVariable Long id) {
        log.info("管理后台手动重试出证 [contractId={}]", id);
        Map<String, Object> resp = new HashMap<>();
        resp.put("contractId", id);
        try {
            CertificateService.CertificateResult r = certificateService.certifyContract(id);
            resp.put("code", r.success() ? 0 : -1);
            resp.put("contractNo", r.contractNo());
            resp.put("certificateNo", r.certificateNo());
            resp.put("status", r.status());
            resp.put("success", r.success());
        } catch (Exception e) {
            log.warn("重试出证失败 [contractId={}]: {}", id, e.getMessage());
            resp.put("code", -1);
            resp.put("success", false);
            resp.put("message", "出证重试失败：" + e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }

    // ========== T20.9: GET /api/admin/contracts ==========

    /**
     * 管理后台合同列表（多维度检索）。
     * <p>
     * 支持按订单号、SN、用户ID、合同编号、状态、日期范围检索。
     *
     * @param page       页码（从 0 开始）
     * @param size       每页大小
     * @param orderId    订单号（模糊匹配）
     * @param deviceSn   设备 SN（精确匹配）
     * @param userId     用户 ID
     * @param contractNo 合同编号（模糊匹配）
     * @param status     合同状态
     * @param startDate  开始日期（yyyy-MM-dd）
     * @param endDate    结束日期（yyyy-MM-dd）
     * @return 合同分页列表
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listContracts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String orderId,
            @RequestParam(required = false) String deviceSn,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String contractNo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        log.info("管理后台合同检索 [page={}, size={}, orderId={}, deviceSn={}, userId={}, contractNo={}, status={}, startDate={}, endDate={}]",
                page, size, orderId, deviceSn, userId, contractNo, status, startDate, endDate);

        LocalDateTime startDt = parseDate(startDate);
        LocalDateTime endDt = parseDate(endDate);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Contract> result = queryService.searchContracts(
                orderId, deviceSn, userId, contractNo, status,
                startDt, endDt, pageable);

        List<Map<String, Object>> items = result.getContent().stream()
                .map(this::toContractSummary)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("code", 0);
        response.put("total", result.getTotalElements());
        response.put("page", result.getNumber());
        response.put("size", result.getSize());
        response.put("totalPages", result.getTotalPages());
        response.put("items", items);

        return ResponseEntity.ok(response);
    }

    // ========== T20.10: GET /api/admin/contracts/{id} ==========

    /**
     * 管理后台合同详情（完整审计信息）。
     *
     * @param id 合同 ID
     * @return 合同完整详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getContractDetail(@PathVariable Long id) {
        log.info("管理后台合同详情查询 [contractId={}]", id);

        Contract contract = queryService.getContractDetail(id);

        // 统计访问次数
        long viewCount = accessLogService.countByContractIdAndAccessType(
                id, ContractAccessLog.AccessType.VIEW);
        long downloadCount = accessLogService.countByContractIdAndAccessType(
                id, ContractAccessLog.AccessType.DOWNLOAD);

        // 腾讯云端原始文件 URL（管理后台可查看）
        String tencentCloudViewUrl = "";
        if (contract.getTencentCloudUrl() != null && !contract.getTencentCloudUrl().isBlank()) {
            tencentCloudViewUrl = contract.getTencentCloudUrl();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("code", 0);
        response.put("id", contract.getId());
        response.put("contractNo", contract.getContractNo());
        response.put("templateId", contract.getTemplateId());
        response.put("userId", contract.getUserId());
        response.put("orderId", contract.getOrderId() != null ? contract.getOrderId() : "");
        response.put("deviceSn", contract.getDeviceSn() != null ? contract.getDeviceSn() : "");
        response.put("status", contract.getStatus().name());
        response.put("essFlowId", contract.getEssFlowId() != null ? contract.getEssFlowId() : "");
        // 审计字段
        response.put("pdfHash", contract.getPdfHash() != null ? contract.getPdfHash() : "");
        response.put("tencentCloudUrl", tencentCloudViewUrl);
        response.put("ossUrl", contract.getOssUrl() != null ? contract.getOssUrl() : "");
        response.put("archiveStatus", contract.getArchiveStatus() != null ? contract.getArchiveStatus().name() : "");
        response.put("certificateNo", contract.getCertificateNo() != null ? contract.getCertificateNo() : "");
        response.put("downloadCount", contract.getDownloadCount());
        response.put("viewAccessCount", viewCount);
        response.put("downloadAccessCount", downloadCount);
        response.put("archivedAt", contract.getArchivedAt() != null ? contract.getArchivedAt().format(DTF) : "");
        response.put("createdAt", contract.getCreatedAt() != null ? contract.getCreatedAt().format(DTF) : "");
        response.put("updatedAt", contract.getUpdatedAt() != null ? contract.getUpdatedAt().format(DTF) : "");

        return ResponseEntity.ok(response);
    }

    // ========== T20.11: GET /api/admin/contracts/{id}/audit ==========

    /**
     * 合同审计记录查询。
     * <p>
     * 返回合同的完整访问日志。
     *
     * @param id    合同 ID
     * @param page  页码
     * @param size  每页大小
     * @return 访问日志列表
     */
    @GetMapping("/{id}/audit")
    public ResponseEntity<Map<String, Object>> getContractAuditLog(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("合同审计记录查询 [contractId={}, page={}, size={}]", id, page, size);

        // 先验证合同存在
        queryService.getContractDetail(id);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ContractAccessLog> logs = accessLogService.getAccessLogs(id, pageable);

        List<Map<String, Object>> items = logs.getContent().stream()
                .map(this::toAccessLogSummary)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("code", 0);
        response.put("contractId", id);
        response.put("total", logs.getTotalElements());
        response.put("page", logs.getNumber());
        response.put("size", logs.getSize());
        response.put("items", items);

        return ResponseEntity.ok(response);
    }

    // ========== T22.8: GET /api/admin/contracts/certificate/{contractId} ==========

    /**
     * 管理后台出证信息查询。
     * <p>
     * 查询合同的出证状态和出证结果信息。
     *
     * @param contractId 合同 ID
     * @return 出证信息
     */
    @GetMapping("/certificate/{contractId}")
    public ResponseEntity<Map<String, Object>> getCertificateInfo(@PathVariable Long contractId) {
        log.info("管理后台出证信息查询 [contractId={}]", contractId);

        Contract contract = queryService.getContractDetail(contractId);

        CertificateService.CertificateResult certResult = certificateService.queryCertificateStatus(contractId);

        Map<String, Object> response = new HashMap<>();
        response.put("code", 0);
        response.put("contractId", contractId);
        response.put("contractNo", contract.getContractNo());
        response.put("certificateStatus", certResult.status());
        response.put("certificateNo", certResult.certificateNo() != null ? certResult.certificateNo() : "");
        response.put("certificatePdfUrl", certResult.certificatePdfUrl() != null ? certResult.certificatePdfUrl() : "");
        response.put("certifiedAt", certResult.certifiedAt() != null ? certResult.certifiedAt() : "");
        response.put("contractStatus", contract.getStatus().name());

        return ResponseEntity.ok(response);
    }

    // ========== T22.9: GET /api/admin/contracts/certificate/{contractId}/download ==========

    /**
     * 管理后台出证 PDF 下载。
     * <p>
     * 返回出证 PDF 的下载 URL。
     *
     * @param contractId 合同 ID
     * @return 出证 PDF 下载信息
     */
    @GetMapping("/certificate/{contractId}/download")
    public ResponseEntity<Map<String, Object>> downloadCertificate(@PathVariable Long contractId) {
        log.info("管理后台出证 PDF 下载请求 [contractId={}]", contractId);

        CertificateService.CertificateResult certResult = certificateService.queryCertificateStatus(contractId);

        if (!certResult.success() || certResult.certificatePdfUrl() == null || certResult.certificatePdfUrl().isBlank()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("code", -1);
            errorResponse.put("message", "合同尚未完成出证或出证 PDF 不可用");
            errorResponse.put("contractId", contractId);
            return ResponseEntity.badRequest().body(errorResponse);
        }

        // 审计事件：管理员下载出证 PDF
        auditTrailService.recordEvent(contractId, ContractAuditTrail.Action.DOWNLOAD,
                null, ContractAuditTrail.ActorType.ADMIN,
                String.format("{\"type\":\"certificate\",\"certificateNo\":\"%s\"}", certResult.certificateNo()), null);

        Map<String, Object> response = new HashMap<>();
        response.put("code", 0);
        response.put("contractId", contractId);
        response.put("certificateNo", certResult.certificateNo());
        response.put("downloadUrl", certResult.certificatePdfUrl());

        return ResponseEntity.ok(response);
    }

    // ========== T22.10: GET /api/admin/contracts/search ==========

    /**
     * 管理后台增强检索。
     * <p>
     * 在原有 listContracts 基础上增加出证状态筛选。
     *
     * @param page              页码（从 0 开始）
     * @param size              每页大小
     * @param orderId           订单号（模糊匹配）
     * @param deviceSn          设备 SN（精确匹配）
     * @param userId            用户 ID
     * @param contractNo        合同编号（模糊匹配）
     * @param status            合同状态
     * @param certificateStatus 出证状态
     * @param startDate         开始日期（yyyy-MM-dd）
     * @param endDate           结束日期（yyyy-MM-dd）
     * @return 合同分页列表
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchContracts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String orderId,
            @RequestParam(required = false) String deviceSn,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String contractNo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String certificateStatus,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        log.info("管理后台增强检索 [page={}, size={}, orderId={}, deviceSn={}, userId={}, contractNo={}, status={}, certStatus={}, startDate={}, endDate={}]",
                page, size, orderId, deviceSn, userId, contractNo, status, certificateStatus, startDate, endDate);

        LocalDateTime startDt = parseDate(startDate);
        LocalDateTime endDt = parseDate(endDate);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Contract> result = queryService.searchContracts(
                orderId, deviceSn, userId, contractNo, status,
                startDt, endDt, pageable);

        // 额外按出证状态筛选（内存过滤）
        List<Contract> filtered = result.getContent().stream()
                .filter(c -> certificateStatus == null || certificateStatus.isBlank()
                        || (c.getCertificateStatus() != null && c.getCertificateStatus().name().equals(certificateStatus))
                        || ("NOT_APPLIED".equals(certificateStatus) && c.getCertificateStatus() == null))
                .collect(Collectors.toList());

        List<Map<String, Object>> items = filtered.stream()
                .map(this::toContractSummaryWithCertificate)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("code", 0);
        response.put("total", result.getTotalElements());
        response.put("page", result.getNumber());
        response.put("size", result.getSize());
        response.put("totalPages", result.getTotalPages());
        response.put("items", items);

        return ResponseEntity.ok(response);
    }

    // ========== T22.11: GET /api/admin/contracts/{id}/audit-trail ==========

    /**
     * 合同审计轨迹查询。
     * <p>
     * 返回合同全生命周期的审计事件（合同审计轨迹表）。
     *
     * @param id    合同 ID
     * @param page  页码
     * @param size  每页大小
     * @return 审计轨迹列表
     */
    @GetMapping("/{id}/audit-trail")
    public ResponseEntity<Map<String, Object>> getAuditTrail(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("管理后台审计轨迹查询 [contractId={}, page={}, size={}]", id, page, size);

        // 先验证合同存在
        queryService.getContractDetail(id);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        org.springframework.data.domain.Page<ContractAuditTrail> trails = auditTrailService.getAuditTrail(id, pageable);

        List<Map<String, Object>> items = trails.getContent().stream()
                .map(this::toAuditTrailItem)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("code", 0);
        response.put("contractId", id);
        response.put("total", trails.getTotalElements());
        response.put("page", trails.getNumber());
        response.put("size", trails.getSize());
        response.put("items", items);

        return ResponseEntity.ok(response);
    }

    // ========== 辅助方法 ==========

    private Map<String, Object> toContractSummary(Contract c) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", c.getId());
        map.put("contractNo", c.getContractNo());
        map.put("userId", c.getUserId());
        map.put("orderId", c.getOrderId() != null ? c.getOrderId() : "");
        map.put("deviceSn", c.getDeviceSn() != null ? c.getDeviceSn() : "");
        map.put("status", c.getStatus().name());
        map.put("archiveStatus", c.getArchiveStatus() != null ? c.getArchiveStatus().name() : "");
        map.put("downloadCount", c.getDownloadCount());
        map.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().format(DTF) : "");
        map.put("updatedAt", c.getUpdatedAt() != null ? c.getUpdatedAt().format(DTF) : "");
        return map;
    }

    private Map<String, Object> toAccessLogSummary(ContractAccessLog log) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", log.getId());
        map.put("contractId", log.getContractId());
        map.put("userId", log.getUserId() != null ? log.getUserId() : "");
        map.put("accessType", log.getAccessType().name());
        map.put("accessSource", log.getAccessSource().name());
        map.put("ipAddress", log.getIpAddress() != null ? log.getIpAddress() : "");
        map.put("userAgent", log.getUserAgent() != null ? log.getUserAgent() : "");
        map.put("createdAt", log.getCreatedAt() != null ? log.getCreatedAt().format(DTF) : "");
        return map;
    }

    private LocalDateTime parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        } catch (Exception e) {
            log.warn("日期解析失败: {}", dateStr);
            return null;
        }
    }

    private Map<String, Object> toContractSummaryWithCertificate(Contract c) {
        Map<String, Object> map = toContractSummary(c);
        map.put("certificateStatus", c.getCertificateStatus() != null ? c.getCertificateStatus().name() : "");
        map.put("certificateNo", c.getCertificateNo() != null ? c.getCertificateNo() : "");
        map.put("certifiedAt", c.getCertifiedAt() != null ? c.getCertifiedAt().format(DTF) : "");
        map.put("pdfHash", c.getPdfHash() != null ? c.getPdfHash() : "");
        map.put("essFlowId", c.getEssFlowId() != null ? c.getEssFlowId() : "");
        return map;
    }

    private Map<String, Object> toAuditTrailItem(ContractAuditTrail trail) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", trail.getId());
        map.put("contractId", trail.getContractId());
        map.put("action", trail.getAction().name());
        map.put("actorId", trail.getActorId() != null ? trail.getActorId() : "");
        map.put("actorType", trail.getActorType() != null ? trail.getActorType().name() : "");
        map.put("metadataJson", trail.getMetadataJson() != null ? trail.getMetadataJson() : "");
        map.put("ipAddress", trail.getIpAddress() != null ? trail.getIpAddress() : "");
        map.put("createdAt", trail.getCreatedAt() != null ? trail.getCreatedAt().format(DTF) : "");
        return map;
    }
}
