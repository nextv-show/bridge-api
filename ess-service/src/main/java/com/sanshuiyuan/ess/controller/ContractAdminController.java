package com.sanshuiyuan.ess.controller;

import com.sanshuiyuan.ess.domain.Contract;
import com.sanshuiyuan.ess.domain.ContractAccessLog;
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
 */
@RestController
@RequestMapping("/api/admin/contracts")
public class ContractAdminController {

    private static final Logger log = LoggerFactory.getLogger(ContractAdminController.class);
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ContractQueryService queryService;
    private final ContractAccessLogService accessLogService;
    private final ContractArchiveService archiveService;

    public ContractAdminController(ContractQueryService queryService,
                                    ContractAccessLogService accessLogService,
                                    ContractArchiveService archiveService) {
        this.queryService = queryService;
        this.accessLogService = accessLogService;
        this.archiveService = archiveService;
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
}
