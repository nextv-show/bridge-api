package com.sanshuiyuan.ess.controller;

import com.sanshuiyuan.ess.service.CertificateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 出证内部接口控制器。
 * <p>
 * T22.4: POST /api/internal/contracts/{id}/trigger-certificate
 * 归档完成后内部触发出证。
 */
@RestController
@RequestMapping("/api/internal/contracts")
public class CertificateInternalController {

    private static final Logger log = LoggerFactory.getLogger(CertificateInternalController.class);

    private final CertificateService certificateService;

    public CertificateInternalController(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    /**
     * 触发合同出证（内部接口）。
     * <p>
     * 归档完成后调用此接口自动触发向腾讯电子签申请出证。
     *
     * @param id 合同 ID
     * @return 出证结果
     */
    @PostMapping("/{id}/trigger-certificate")
    public ResponseEntity<Map<String, Object>> triggerCertificate(@PathVariable Long id) {
        log.info("触发出证 [contractId={}]", id);

        try {
            CertificateService.CertificateResult result = certificateService.certifyContract(id);

            Map<String, Object> response = new HashMap<>();
            response.put("code", 0);
            response.put("contractId", result.contractId());
            response.put("contractNo", result.contractNo());
            response.put("certificateNo", result.certificateNo());
            response.put("status", result.status());
            response.put("success", result.success());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("出证触发失败 [contractId={}]: {}", id, e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("code", -1);
            response.put("message", e.getMessage());
            response.put("contractId", id);
            response.put("success", false);

            return ResponseEntity.internalServerError().body(response);
        }
    }
}
