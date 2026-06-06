package com.sanshuiyuan.ess.controller;

import com.sanshuiyuan.ess.auth.CurrentOpenid;
import com.sanshuiyuan.ess.domain.AgreementAcceptance;
import com.sanshuiyuan.ess.domain.ContractTemplate;
import com.sanshuiyuan.ess.dto.AgreementDto.AcceptRequest;
import com.sanshuiyuan.ess.dto.AgreementDto.AcceptanceRecord;
import com.sanshuiyuan.ess.dto.AgreementDto.AcceptanceStatus;
import com.sanshuiyuan.ess.dto.AgreementDto.AgreementDetail;
import com.sanshuiyuan.ess.dto.AgreementDto.AgreementSummary;
import com.sanshuiyuan.ess.service.AgreementService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 协议控制器。
 * <p>
 * 提供 H5/小程序/App 端协议列表、详情查询，以及用户同意协议、查询同意记录与同意状态的端点。
 */
@RestController
@RequestMapping("/api/c/agreements")
public class AgreementController {

    private static final Logger log = LoggerFactory.getLogger(AgreementController.class);

    private final AgreementService agreementService;

    public AgreementController(AgreementService agreementService) {
        this.agreementService = agreementService;
    }

    /** GET /api/c/agreements — 列出所有活跃协议（摘要，无 content_body）。 */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listAgreements() {
        List<AgreementSummary> data = agreementService.getAllActiveAgreements().stream()
                .map(t -> new AgreementSummary(t.getTemplateCode(), t.getTemplateName(), t.getVersion()))
                .toList();
        return ResponseEntity.ok(Map.of("code", 0, "data", data));
    }

    /** GET /api/c/agreements/{code} — 获取协议详情（含 content_body）。 */
    @GetMapping("/{code}")
    public ResponseEntity<Map<String, Object>> getAgreement(@PathVariable String code) {
        ContractTemplate t = agreementService.getLatestAgreement(code);
        AgreementDetail data = new AgreementDetail(
                t.getTemplateCode(), t.getTemplateName(), t.getVersion(), t.getContentBody());
        return ResponseEntity.ok(Map.of("code", 0, "data", data));
    }

    /** POST /api/c/agreements/{code}/accept — 用户同意协议。 */
    @PostMapping("/{code}/accept")
    public ResponseEntity<Map<String, Object>> acceptAgreement(
            @PathVariable String code,
            @RequestBody(required = false) AcceptRequest body,
            HttpServletRequest request) {
        String openid = CurrentOpenid.require();
        String clientType = (body != null && body.clientType() != null && !body.clientType().isBlank())
                ? body.clientType() : "H5";
        String clientIp = request.getRemoteAddr();

        log.info("用户同意协议 [openid={}, code={}, clientType={}]", openid, code, clientType);

        AgreementAcceptance acceptance = agreementService.recordAcceptance(openid, code, clientType, clientIp);
        AcceptanceRecord data = toRecord(acceptance);
        return ResponseEntity.ok(Map.of("code", 0, "data", data));
    }

    /** GET /api/c/agreements/acceptances — 当前用户的同意记录。 */
    @GetMapping("/acceptances")
    public ResponseEntity<Map<String, Object>> getUserAcceptances() {
        String openid = CurrentOpenid.require();
        List<AcceptanceRecord> data = agreementService.getUserAcceptances(openid).stream()
                .map(this::toRecord)
                .toList();
        return ResponseEntity.ok(Map.of("code", 0, "data", data));
    }

    /** GET /api/c/agreements/{code}/status — 检查当前用户是否已同意某协议。 */
    @GetMapping("/{code}/status")
    public ResponseEntity<Map<String, Object>> getAcceptanceStatus(@PathVariable String code) {
        String openid = CurrentOpenid.require();
        boolean accepted = agreementService.hasAccepted(openid, code);
        AcceptanceStatus data = new AcceptanceStatus(code, accepted);
        return ResponseEntity.ok(Map.of("code", 0, "data", data));
    }

    private AcceptanceRecord toRecord(AgreementAcceptance a) {
        LocalDateTime acceptedAt = a.getAcceptedAt();
        return new AcceptanceRecord(
                a.getAgreementCode(),
                a.getTemplateVersion(),
                acceptedAt != null ? acceptedAt.toString() : null,
                a.getClientType());
    }
}
