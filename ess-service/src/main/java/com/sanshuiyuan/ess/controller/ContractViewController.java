package com.sanshuiyuan.ess.controller;

import com.sanshuiyuan.ess.domain.Contract;
import com.sanshuiyuan.ess.domain.ContractAccessLog.AccessType;
import com.sanshuiyuan.ess.domain.ContractAccessLog.AccessSource;
import com.sanshuiyuan.ess.infra.repository.ContractSnBindingRepository;
import com.sanshuiyuan.ess.service.ContractAccessLogService;
import com.sanshuiyuan.ess.service.ContractArchiveService;
import com.sanshuiyuan.ess.service.ContractQueryService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 合同查看与下载控制器（H5 端）。
 * <p>
 * T20.6: GET /api/h5/contracts/{id}/view — 从 OSS 读取合同 PDF（<3s）
 * T20.7: GET /api/h5/contracts/{id}/download — 下载合同 PDF + 记录访问日志
 * T20.8: GET /api/h5/devices/{sn}/contract — 通过 SN 查询关联合同
 */
@RestController
@RequestMapping("/api/h5")
public class ContractViewController {

    private static final Logger log = LoggerFactory.getLogger(ContractViewController.class);

    private final ContractArchiveService archiveService;
    private final ContractQueryService queryService;
    private final ContractAccessLogService accessLogService;
    private final ContractSnBindingRepository snBindingRepository;

    public ContractViewController(ContractArchiveService archiveService,
                                   ContractQueryService queryService,
                                   ContractAccessLogService accessLogService,
                                   ContractSnBindingRepository snBindingRepository) {
        this.archiveService = archiveService;
        this.queryService = queryService;
        this.accessLogService = accessLogService;
        this.snBindingRepository = snBindingRepository;
    }

    // ========== T20.6: GET /contracts/{id}/view ==========

    /**
     * 用户查看合同 PDF（从自有 OSS 读取，3s 内可打开）。
     * <p>
     * 返回带签名的临时 OSS URL，前端直接加载。
     *
     * @param id      合同 ID
     * @param request HTTP 请求（用于获取 IP 和 User-Agent）
     * @return 签名 URL
     */
    @GetMapping("/contracts/{id}/view")
    public ResponseEntity<Map<String, Object>> viewContract(
            @PathVariable Long id,
            HttpServletRequest request) {

        log.info("合同查看请求 [contractId={}]", id);

        // 获取签名 URL
        String viewUrl = archiveService.getViewUrl(id);

        // 记录访问日志
        String ipAddress = getClientIpAddress(request);
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        accessLogService.logAccess(id, null, AccessType.VIEW, AccessSource.H5, ipAddress, userAgent);

        // 获取合同信息
        Contract contract = queryService.getContractDetail(id);

        return ResponseEntity.ok(Map.of(
                "code", 0,
                "contractId", id,
                "contractNo", contract.getContractNo(),
                "status", contract.getStatus().name(),
                "archiveStatus", contract.getArchiveStatus() != null ? contract.getArchiveStatus().name() : "",
                "viewUrl", viewUrl,
                "pdfHash", contract.getPdfHash() != null ? contract.getPdfHash() : ""
        ));
    }

    // ========== T20.7: GET /contracts/{id}/download ==========

    /**
     * 用户下载合同 PDF。
     * <p>
     * 返回带签名的临时下载 URL，并记录访问日志。
     *
     * @param id      合同 ID
     * @param request HTTP 请求
     * @return 签名下载 URL
     */
    @GetMapping("/contracts/{id}/download")
    public ResponseEntity<Map<String, Object>> downloadContract(
            @PathVariable Long id,
            HttpServletRequest request) {

        log.info("合同下载请求 [contractId={}]", id);

        // 获取下载 URL
        String downloadUrl = archiveService.getDownloadUrl(id);

        // 记录访问日志
        String ipAddress = getClientIpAddress(request);
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        accessLogService.logAccess(id, null, AccessType.DOWNLOAD, AccessSource.H5, ipAddress, userAgent);

        // 获取合同信息
        Contract contract = queryService.getContractDetail(id);

        return ResponseEntity.ok(Map.of(
                "code", 0,
                "contractId", id,
                "contractNo", contract.getContractNo(),
                "status", contract.getStatus().name(),
                "downloadUrl", downloadUrl,
                "pdfHash", contract.getPdfHash() != null ? contract.getPdfHash() : ""
        ));
    }

    // ========== T20.8: GET /devices/{sn}/contract ==========

    /**
     * 通过设备 SN 查询关联合同。
     * <p>
     * 用于"我的设备"详情页合同入口。
     *
     * @param sn      设备 SN
     * @return 合同信息
     */
    @GetMapping("/devices/{sn}/contract")
    public ResponseEntity<Map<String, Object>> getDeviceContract(@PathVariable String sn) {
        log.info("通过 SN 查询合同 [sn={}]", sn);

        // 先通过 SN 绑定查找合同
        var bindings = snBindingRepository.findByDeviceSn(sn);
        if (bindings.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "code", 1,
                    "message", "未找到该设备关联合同"
            ));
        }

        // 取第一个有效绑定的合同
        var binding = bindings.get(0);
        Long contractId = binding.getContractId();

        Contract contract = queryService.getContractDetail(contractId);

        // 检查是否已归档，如已归档返回查看 URL
        String viewUrl = "";
        if (contract.getArchiveStatus() == Contract.ArchiveStatus.ARCHIVED) {
            viewUrl = archiveService.getViewUrl(contractId);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("code", 0);
        response.put("contractId", contract.getId());
        response.put("contractNo", contract.getContractNo());
        response.put("status", contract.getStatus().name());
        response.put("archiveStatus", contract.getArchiveStatus() != null ? contract.getArchiveStatus().name() : "");
        response.put("viewUrl", viewUrl);
        response.put("orderId", contract.getOrderId() != null ? contract.getOrderId() : "");
        response.put("deviceSn", sn);
        response.put("createdAt", contract.getCreatedAt() != null ? contract.getCreatedAt().toString() : "");

        return ResponseEntity.ok(response);
    }

    /**
     * 获取客户端真实 IP 地址。
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        // X-Forwarded-For 可能包含多个 IP，取第一个
        if (ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
