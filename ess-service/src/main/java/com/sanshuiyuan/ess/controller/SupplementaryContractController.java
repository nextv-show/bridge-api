package com.sanshuiyuan.ess.controller;

import com.sanshuiyuan.ess.domain.RefundContractLinkage;
import com.sanshuiyuan.ess.domain.SupplementaryContract;
import com.sanshuiyuan.ess.infra.repository.RefundContractLinkageRepository;
import com.sanshuiyuan.ess.service.IdempotentService;
import com.sanshuiyuan.ess.service.SupplementaryContractService;
import com.sanshuiyuan.ess.service.SupplementaryContractService.SupplementarySignResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 补充协议控制器。
 * <p>
 * 提供补充协议生成、签署发起、回调、状态查询端点。
 */
@RestController
@RequestMapping("/api/h5/refunds")
public class SupplementaryContractController {

    private static final Logger log = LoggerFactory.getLogger(SupplementaryContractController.class);

    private final SupplementaryContractService supplementaryContractService;
    private final RefundContractLinkageRepository linkageRepository;
    private final IdempotentService idempotentService;

    public SupplementaryContractController(SupplementaryContractService supplementaryContractService,
                                            RefundContractLinkageRepository linkageRepository,
                                            IdempotentService idempotentService) {
        this.supplementaryContractService = supplementaryContractService;
        this.linkageRepository = linkageRepository;
        this.idempotentService = idempotentService;
    }

    // ========== Task 21.11: POST /{refundId}/supplementary-contract/generate ==========

    /**
     * 生成补充协议。
     *
     * @param refundId 退款订单 ID
     * @param request  包含 originalContractId, signerInfoJson, contractFieldsJson
     * @return 补充协议信息
     */
    @PostMapping("/{refundId}/supplementary-contract/generate")
    public ResponseEntity<Map<String, Object>> generateSupplementaryContract(
            @PathVariable String refundId,
            @RequestBody Map<String, String> request) {

        String idempotentKey = refundId + ":GENERATE_SC";

        Long originalContractId = Long.parseLong(request.getOrDefault("originalContractId", "0"));
        if (originalContractId == 0) {
            throw new IllegalArgumentException("缺少必要参数: originalContractId");
        }

        log.info("生成补充协议 [refundId={}, originalContractId={}]", refundId, originalContractId);

        // 幂等检查
        String existing = idempotentService.getExistingResult(idempotentKey);
        if (existing != null) {
            SupplementaryContract sc = supplementaryContractService.getByRefundOrderId(refundId);
            return ResponseEntity.ok(Map.of(
                    "code", 0,
                    "message", "补充协议已生成（幂等）",
                    "scId", sc.getId(),
                    "contractNo", sc.getContractNo(),
                    "status", sc.getStatus().name(),
                    "idempotent", true
            ));
        }

        String signerInfoJson = request.getOrDefault("signerInfoJson", "{}");
        String contractFieldsJson = request.getOrDefault("contractFieldsJson", "{}");

        SupplementaryContract sc = supplementaryContractService.generateSupplementaryContract(
                refundId, originalContractId, signerInfoJson, contractFieldsJson);

        idempotentService.recordResult(idempotentKey, String.valueOf(sc.getId()));

        return ResponseEntity.ok(Map.of(
                "code", 0,
                "message", "补充协议生成成功",
                "scId", sc.getId(),
                "contractNo", sc.getContractNo(),
                "status", sc.getStatus().name(),
                "originalContractId", sc.getOriginalContractId()
        ));
    }

    // ========== Task 21.12: POST /{refundId}/supplementary-contract/{scId}/initiate-signing ==========

    /**
     * 发起补充协议签署。
     *
     * @param refundId 退款订单 ID
     * @param scId     补充协议 ID
     * @return 签署结果
     */
    @PostMapping("/{refundId}/supplementary-contract/{scId}/initiate-signing")
    public ResponseEntity<Map<String, Object>> initiateSigning(
            @PathVariable String refundId,
            @PathVariable Long scId) {

        String idempotentKey = scId + ":INITIATE_SIGNING";

        log.info("发起补充协议签署 [refundId={}, scId={}]", refundId, scId);

        // 幂等检查
        String existing = idempotentService.getExistingResult(idempotentKey);
        if (existing != null) {
            SupplementaryContract sc = supplementaryContractService.getSupplementaryContract(scId);
            return ResponseEntity.ok(Map.of(
                    "code", 0,
                    "message", "签署已发起（幂等）",
                    "scId", scId,
                    "essFlowId", sc.getEssFlowId() != null ? sc.getEssFlowId() : "",
                    "status", sc.getStatus().name(),
                    "idempotent", true
            ));
        }

        SupplementarySignResult result = supplementaryContractService.initiateSigning(scId);

        idempotentService.recordResult(idempotentKey, result.essFlowId());

        return ResponseEntity.ok(Map.of(
                "code", 0,
                "message", "签署流程已创建",
                "scId", result.scId(),
                "contractNo", result.contractNo(),
                "essFlowId", result.essFlowId(),
                "status", result.status()
        ));
    }

    // ========== Task 21.13: POST /{refundId}/supplementary-contract/{scId}/callback ==========

    /**
     * 补充协议签署完成回调。
     *
     * @param refundId     退款订单 ID
     * @param scId         补充协议 ID
     * @param callbackData 回调数据
     * @return 处理结果
     */
    @PostMapping("/{refundId}/supplementary-contract/{scId}/callback")
    public ResponseEntity<Map<String, Object>> signingCallback(
            @PathVariable String refundId,
            @PathVariable Long scId,
            @RequestBody Map<String, Object> callbackData) {

        log.info("收到补充协议签署回调 [refundId={}, scId={}]", refundId, scId);

        try {
            String pdfUrl = callbackData.get("PdfUrl") != null
                    ? callbackData.get("PdfUrl").toString() : null;
            String pdfHash = callbackData.get("PdfHash") != null
                    ? callbackData.get("PdfHash").toString() : null;

            supplementaryContractService.completeSigning(scId, pdfUrl, pdfHash);

            return ResponseEntity.ok(Map.of(
                    "code", 0,
                    "message", "签署回调处理成功"
            ));
        } catch (Exception e) {
            log.error("补充协议签署回调处理失败 [scId={}]: {}", scId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "code", -1,
                    "message", "回调处理失败: " + e.getMessage()
            ));
        }
    }

    // ========== Task 21.14: GET /{refundId}/supplementary-contract/status ==========

    /**
     * 查询补充协议状态。
     *
     * @param refundId 退款订单 ID
     * @return 补充协议状态
     */
    @GetMapping("/{refundId}/supplementary-contract/status")
    public ResponseEntity<Map<String, Object>> getSupplementaryContractStatus(
            @PathVariable String refundId) {

        log.debug("查询补充协议状态 [refundId={}]", refundId);

        SupplementaryContract sc = supplementaryContractService.getByRefundOrderId(refundId);

        // 查询联动状态
        String linkageStatus = linkageRepository.findByRefundOrderId(refundId)
                .map(RefundContractLinkage::getLinkageStatus)
                .map(Enum::name)
                .orElse("NONE");

        return ResponseEntity.ok(Map.of(
                "code", 0,
                "scId", sc.getId(),
                "contractNo", sc.getContractNo(),
                "status", sc.getStatus().name(),
                "originalContractId", sc.getOriginalContractId(),
                "essFlowId", sc.getEssFlowId() != null ? sc.getEssFlowId() : "",
                "pdfUrl", sc.getPdfUrl() != null ? sc.getPdfUrl() : "",
                "signedAt", sc.getSignedAt() != null ? sc.getSignedAt().toString() : "",
                "linkageStatus", linkageStatus
        ));
    }
}
