package com.sanshuiyuan.ess.controller;

import com.sanshuiyuan.ess.domain.Contract;
import com.sanshuiyuan.ess.infra.repository.ContractRepository;
import com.sanshuiyuan.ess.service.ContractGenerationService;
import com.sanshuiyuan.ess.service.ContractGenerationService.GenerateContractRequest;
import com.sanshuiyuan.ess.service.ContractGenerationService.GenerateContractResult;
import com.sanshuiyuan.ess.service.ContractSigningService;
import com.sanshuiyuan.ess.service.ContractSigningService.SigningInitiationResult;
import com.sanshuiyuan.ess.service.ContractStateMachineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 合同生成与管理控制器。
 * <p>
 * 提供 H5 端合同生成、预览、签署发起、回调、状态查询等全流程端点。
 */
@RestController
@RequestMapping("/api/h5/contracts")
public class ContractController {

    private static final Logger log = LoggerFactory.getLogger(ContractController.class);

    private final ContractGenerationService generationService;
    private final ContractSigningService signingService;
    private final ContractStateMachineService stateMachineService;
    private final ContractRepository contractRepository;

    public ContractController(ContractGenerationService generationService,
                               ContractSigningService signingService,
                               ContractStateMachineService stateMachineService,
                               ContractRepository contractRepository) {
        this.generationService = generationService;
        this.signingService = signingService;
        this.stateMachineService = stateMachineService;
        this.contractRepository = contractRepository;
    }

    // ========== T17.9: POST /generate ==========

    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generateContract(
            @RequestBody Map<String, String> request) {

        Long userId = requireLong(request, "userId");
        String orderId = request.getOrDefault("orderId", "");
        String deviceSn = request.get("deviceSn");
        String deviceModel = requireParam(request, "deviceModel");
        String devicePrice = requireParam(request, "devicePrice");
        String userName = requireParam(request, "userName");
        String idCardNo = requireParam(request, "idCardNo");
        String phone = requireParam(request, "phone");

        log.info("合同生成请求 [userId={}, deviceSn={}, deviceModel={}]",
                userId, deviceSn, deviceModel);

        GenerateContractRequest genRequest = new GenerateContractRequest(
                userId, orderId, deviceSn, deviceModel, devicePrice,
                userName, idCardNo, phone);

        GenerateContractResult result = generationService.generateContract(genRequest);

        return ResponseEntity.ok(Map.of(
                "code", 0,
                "message", "合同生成成功",
                "contractId", result.contractId(),
                "contractNo", result.contractNo(),
                "status", result.status().name(),
                "mainContract", result.mainContractContent(),
                "attachment", result.attachmentContent()
        ));
    }

    // ========== T17.10: GET /{id}/preview ==========

    @GetMapping("/{id}/preview")
    public ResponseEntity<Map<String, Object>> previewContract(@PathVariable Long id) {
        log.debug("合同预览请求 [contractId={}]", id);

        GenerateContractResult result = generationService.getContractContent(id);

        return ResponseEntity.ok(Map.of(
                "code", 0,
                "contractId", result.contractId(),
                "contractNo", result.contractNo(),
                "status", result.status().name(),
                "mainContract", result.mainContractContent(),
                "attachment", result.attachmentContent()
        ));
    }

    // ========== T17.12: POST /{id}/initiate-signing ==========

    /**
     * 发起签署流程。
     * <p>
     * 调用腾讯电子签创建签署流程，合同状态 GENERATED → SIGNING。
     *
     * @param id      合同 ID
     * @param request 包含 userId
     * @return 签署流程信息
     */
    @PostMapping("/{id}/initiate-signing")
    public ResponseEntity<Map<String, Object>> initiateSigning(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {

        Long userId = requireLong(request, "userId");
        log.info("发起签署 [contractId={}, userId={}]", id, userId);

        SigningInitiationResult result = signingService.initiateSigning(id, userId);

        return ResponseEntity.ok(Map.of(
                "code", 0,
                "message", "签署流程已创建",
                "contractId", result.contractId(),
                "contractNo", result.contractNo(),
                "essFlowId", result.essFlowId(),
                "status", result.status().name()
        ));
    }

    // ========== T17.13: POST /{id}/callback ==========

    /**
     * 腾讯电子签签署完成 Webhook 回调。
     *
     * @param id           合同 ID
     * @param callbackData 回调数据
     * @return 处理结果
     */
    @PostMapping("/{id}/callback")
    public ResponseEntity<Map<String, Object>> signingCallback(
            @PathVariable Long id,
            @RequestBody Map<String, Object> callbackData) {

        log.info("收到签署回调 [contractId={}]", id);

        try {
            String pdfUrl = callbackData.get("PdfUrl") != null
                    ? callbackData.get("PdfUrl").toString() : null;
            String pdfHash = callbackData.get("PdfHash") != null
                    ? callbackData.get("PdfHash").toString() : null;

            signingService.completeSigning(id, pdfUrl, pdfHash);

            return ResponseEntity.ok(Map.of(
                    "code", 0,
                    "message", "签署回调处理成功"
            ));
        } catch (Exception e) {
            log.error("签署回调处理失败 [contractId={}]: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "code", -1,
                    "message", "回调处理失败: " + e.getMessage()
            ));
        }
    }

    // ========== T17.14: GET /{id}/status ==========

    /**
     * 查询签署状态。
     *
     * @param id 合同 ID
     * @return 签署状态
     */
    @GetMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> getContractStatus(@PathVariable Long id) {
        Contract contract = stateMachineService.getContract(id);

        return ResponseEntity.ok(Map.of(
                "code", 0,
                "contractId", id,
                "contractNo", contract.getContractNo(),
                "status", contract.getStatus().name(),
                "essFlowId", contract.getEssFlowId() != null ? contract.getEssFlowId() : "",
                "pdfUrl", contract.getPdfUrl() != null ? contract.getPdfUrl() : "",
                "createdAt", contract.getCreatedAt() != null ? contract.getCreatedAt().toString() : "",
                "updatedAt", contract.getUpdatedAt() != null ? contract.getUpdatedAt().toString() : ""
        ));
    }

    private String requireParam(Map<String, String> request, String key) {
        String value = request.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("缺少必要参数: " + key);
        }
        return value;
    }

    private Long requireLong(Map<String, String> request, String key) {
        String value = requireParam(request, key);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("参数格式错误: " + key + " 必须为数字");
        }
    }
}
