package com.sanshuiyuan.ess.controller;

import com.sanshuiyuan.ess.service.ContractGenerationService;
import com.sanshuiyuan.ess.service.ContractGenerationService.GenerateContractRequest;
import com.sanshuiyuan.ess.service.ContractGenerationService.GenerateContractResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 合同生成与管理控制器。
 * <p>
 * 提供 H5 端合同生成、预览、签署发起、状态查询等全流程端点。
 */
@RestController
@RequestMapping("/api/h5/contracts")
public class ContractController {

    private static final Logger log = LoggerFactory.getLogger(ContractController.class);

    private final ContractGenerationService generationService;

    public ContractController(ContractGenerationService generationService) {
        this.generationService = generationService;
    }

    // ========== T17.9: POST /generate ==========

    /**
     * 生成合同。
     * <p>
     * 接收用户信息、设备信息，生成主合同+产权归属确认书附件。
     *
     * @param request 包含 userId, orderId, deviceSn, deviceModel, devicePrice, userName, idCardNo, phone
     * @return 生成结果
     */
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

    /**
     * 合同预览。
     * <p>
     * 返回主合同+附件预览内容。
     *
     * @param id 合同 ID
     * @return 合同预览数据
     */
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
