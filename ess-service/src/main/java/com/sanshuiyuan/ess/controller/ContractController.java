package com.sanshuiyuan.ess.controller;

import com.sanshuiyuan.ess.auth.ContractOwnershipGuard;
import com.sanshuiyuan.ess.auth.CurrentOpenid;
import com.sanshuiyuan.ess.domain.Contract;
import com.sanshuiyuan.ess.config.ClientTypeInterceptor;
import com.sanshuiyuan.ess.config.ClientTypeInterceptor.ClientType;
import com.sanshuiyuan.ess.infra.client.UserServiceClient;
import com.sanshuiyuan.ess.infra.repository.ContractRepository;
import com.sanshuiyuan.ess.service.ContractGenerationService;
import com.sanshuiyuan.ess.service.ContractGenerationService.GenerateContractRequest;
import com.sanshuiyuan.ess.service.ContractGenerationService.GenerateContractResult;
import com.sanshuiyuan.ess.service.ContractSigningService;
import com.sanshuiyuan.ess.service.ContractSigningService.SigningInitiationResult;
import com.sanshuiyuan.ess.service.ContractStateMachineService;
import com.sanshuiyuan.ess.service.MultiPlatformSignService;
import com.sanshuiyuan.ess.service.SignStatusSyncService;
import jakarta.servlet.http.HttpServletRequest;
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
@RequestMapping("/api/c/contracts")
public class ContractController {

    private static final Logger log = LoggerFactory.getLogger(ContractController.class);

    private final ContractGenerationService generationService;
    private final ContractSigningService signingService;
    private final ContractStateMachineService stateMachineService;
    private final ContractRepository contractRepository;
    private final MultiPlatformSignService multiPlatformSignService;
    private final SignStatusSyncService signStatusSyncService;
    private final ContractOwnershipGuard ownershipGuard;
    private final UserServiceClient userServiceClient;

    /** 小程序签署默认 appId（前端未显式传 wxAppId 时回退）。 */
    @org.springframework.beans.factory.annotation.Value("${wx.miniprogram.app-id:}")
    private String defaultMiniAppId;

    public ContractController(ContractGenerationService generationService,
                               ContractSigningService signingService,
                               ContractStateMachineService stateMachineService,
                               ContractRepository contractRepository,
                               MultiPlatformSignService multiPlatformSignService,
                               SignStatusSyncService signStatusSyncService,
                               ContractOwnershipGuard ownershipGuard,
                               UserServiceClient userServiceClient) {
        this.generationService = generationService;
        this.signingService = signingService;
        this.stateMachineService = stateMachineService;
        this.contractRepository = contractRepository;
        this.multiPlatformSignService = multiPlatformSignService;
        this.signStatusSyncService = signStatusSyncService;
        this.ownershipGuard = ownershipGuard;
        this.userServiceClient = userServiceClient;
    }

    /** 加载合同；不存在抛 404。owner 校验前先确定合同归属。 */
    private Contract loadContractOrThrow(Long id) {
        return contractRepository.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "合同不存在: id=" + id));
    }

    // ========== T17.9: POST /generate ==========

    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generateContract(
            @RequestBody Map<String, String> request) {

        // 不信任 body 的 userId：以当前 H5 会话 openid 解析出的 userId 为准，防止替他人生成合同。
        String openid = CurrentOpenid.require();
        Long userId = userServiceClient.resolveUserId(openid);
        if (userId == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "无法解析当前用户身份");
        }
        // 合同用途（spec 107）：默认设备认购主合同；KYC_AUTH 为实名承诺书，与设备合同模板隔离。
        ContractGenerationService.ContractPurpose purpose =
                ContractGenerationService.ContractPurpose.parse(request.get("contractPurpose"));
        boolean isKycAuth = purpose == ContractGenerationService.ContractPurpose.KYC_AUTH;

        // 服务边界强制隔离：KYC_AUTH 实名承诺书绝不接收设备/订单字段，即便请求注入也一律清空，
        // 防止绕过 cend 直接构造 KYC_AUTH 合同预占任意 SN（承诺书合同与设备认购合同隔离）。
        String orderId = isKycAuth ? "" : request.getOrDefault("orderId", "");
        String deviceSn = isKycAuth ? null : request.get("deviceSn");
        // 设备型号/价格仅设备认购合同必填；实名承诺书不涉及设备，强制置空。
        String deviceModel = isKycAuth ? "" : requireParam(request, "deviceModel");
        String devicePrice = isKycAuth ? "" : requireParam(request, "devicePrice");
        String userName = requireParam(request, "userName");
        String idCardNo = requireParam(request, "idCardNo");
        String phone = requireParam(request, "phone");

        log.info("合同生成请求 [userId={}, purpose={}, deviceSn={}, deviceModel={}]",
                userId, purpose, deviceSn, isKycAuth ? "(无)" : deviceModel);

        GenerateContractRequest genRequest = new GenerateContractRequest(
                userId, orderId, deviceSn, deviceModel, devicePrice,
                userName, idCardNo, phone, purpose);

        GenerateContractResult result = generationService.generateContract(genRequest);

        Map<String, Object> resp = new java.util.LinkedHashMap<>();
        resp.put("code", 0);
        resp.put("message", "合同生成成功");
        resp.put("contractId", result.contractId());
        resp.put("contractNo", result.contractNo());
        resp.put("status", result.status().name());
        resp.put("mainContract", result.mainContractContent());
        return ResponseEntity.ok(resp);
    }

    // ========== T17.10: GET /{id}/preview ==========

    @GetMapping("/{id}/preview")
    public ResponseEntity<Map<String, Object>> previewContract(@PathVariable Long id) {
        log.debug("合同预览请求 [contractId={}]", id);

        ownershipGuard.requireAuthenticated();
        ownershipGuard.assertOwner(loadContractOrThrow(id).getUserId());

        GenerateContractResult result = generationService.getContractContent(id);

        Map<String, Object> resp = new java.util.LinkedHashMap<>();
        resp.put("code", 0);
        resp.put("contractId", result.contractId());
        resp.put("contractNo", result.contractNo());
        resp.put("status", result.status().name());
        resp.put("mainContract", result.mainContractContent());
        return ResponseEntity.ok(resp);
    }

    // ========== T17.12: POST /{id}/initiate-signing ==========

    /**
     * 发起签署流程（多端统一）。
     * <p>
     * 根据 X-Client-Type 请求头或 clientType 参数确定签署来源，
     * 调用腾讯电子签创建签署流程，合同状态 GENERATED → SIGNING。
     *
     * @param id      合同 ID
     * @param request 包含 userId, clientType (可选)
     * @return 签署流程信息
     */
    @PostMapping("/{id}/initiate-signing")
    public ResponseEntity<Map<String, Object>> initiateSigning(
            @PathVariable Long id,
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {

        ownershipGuard.requireAuthenticated();
        ownershipGuard.assertOwner(loadContractOrThrow(id).getUserId());

        // 不信任 body 的 userId：以当前会话 openid 解析（与 /generate 一致），避免上游传 "null" 致 500。
        String openid = CurrentOpenid.require();
        Long userId = userServiceClient.resolveUserId(openid);
        if (userId == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "无法解析当前用户身份");
        }
        ClientType clientType = resolveClientType(request, httpRequest);
        String phoneOverride = request.get("phone");
        String nameOverride = request.get("realName");
        String idCardOverride = request.get("realIdCard");
        // notify=true：小程序「短信短链」签署——签署方 NotifyType=SMS 并立即 StartFlow，
        // 由腾讯电子签下发带签署短链的短信；H5/其它端不传，沿用既有行为。
        boolean smsNotify = "true".equalsIgnoreCase(request.get("notify"));

        log.info("发起签署 [contractId={}, userId={}, clientType={}, phoneOverride={}, nameOverride={}, idCardOverride={}]",
                id, userId, clientType,
                phoneOverride != null ? "***" : "null",
                nameOverride != null ? "**" : "null",
                idCardOverride != null ? "***" : "null");

        Contract.SignSource signSource = mapToSignSource(clientType);
        String[] overrides = (phoneOverride != null || nameOverride != null || idCardOverride != null)
                ? new String[]{phoneOverride, nameOverride, idCardOverride} : null;
        SigningInitiationResult result = signingService.initiateSigning(id, userId, signSource, overrides, smsNotify);

        return ResponseEntity.ok(Map.of(
                "code", 0,
                "message", "签署流程已创建",
                "contractId", result.contractId(),
                "contractNo", result.contractNo(),
                "essFlowId", result.essFlowId(),
                "status", result.status().name(),
                "signSource", result.signSource() != null ? result.signSource().name() : ""
        ));
    }

    // ========== T23.4: GET /{id}/sign-params?clientType= ==========

    /**
     * 获取多端适配签署参数。
     * <p>
     * 根据 clientType 返回对应的签署参数：
     * - H5: 返回签署 URL
     * - MINI: 返回小程序签署参数 JSON
     * - APP: 返回 App 签署参数 JSON
     *
     * @param id           合同 ID
     * @param clientType   客户端类型 (H5/MINI/APP)，可选，默认从请求头解析
     * @param httpRequest  HTTP 请求
     * @return 签署参数
     */
    @GetMapping("/{id}/sign-params")
    public ResponseEntity<Map<String, Object>> getSignParams(
            @PathVariable Long id,
            @RequestParam(required = false) String clientType,
            @RequestParam(required = false) String wxAppId,
            HttpServletRequest httpRequest) {

        ClientType ct = clientType != null && !clientType.isBlank()
                ? ClientTypeInterceptor.parseClientType(clientType)
                : ClientTypeInterceptor.resolve(httpRequest);

        log.info("获取签署参数 [contractId={}, clientType={}]", id, ct);

        ownershipGuard.requireAuthenticated();
        Contract contract = stateMachineService.getContract(id);

        ownershipGuard.assertOwner(contract.getUserId());

        if (contract.getStatus() != Contract.ContractStatus.SIGNING) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", -1,
                    "message", "合同不在签署中状态",
                    "currentStatus", contract.getStatus().name()
            ));
        }

        String contractId = contract.getContractNo();
        String signerId = "1"; // 默认签署人 ID，实际应从合同签署方信息中获取

        java.util.Map<String, String> options = new java.util.HashMap<>();
        // 使用绝对 URL，确保 ESS 回调能正确跳回 H5 页面
        // CheckoutPage 会在挂载时从 URL 恢复签约状态
        options.put("jumpUrl", "https://h5.sanshuiyuan.com/checkout?contractId=" + id);
        options.put("h5Type", "jump");
        options.put("appType", "android");
        // 小程序签署：把小程序 appId 透传给 ESS CreateSchemeUrl（前端传 wxAppId 优先，否则用服务端默认配置）。
        if (ct == ClientType.MINI) {
            String mpAppId = (wxAppId != null && !wxAppId.isBlank()) ? wxAppId : defaultMiniAppId;
            if (mpAppId != null && !mpAppId.isBlank()) {
                options.put("wxAppId", mpAppId);
            }
        }

        MultiPlatformSignService.SignParamsResult result =
                multiPlatformSignService.generateSignParams(contractId, signerId, ct, options);

        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("code", 0);
        response.put("contractId", id);
        response.put("contractNo", contract.getContractNo());
        response.put("clientType", ct.name());
        response.put("signMethod", result.signMethod());

        if (result.signUrl() != null) {
            response.put("signUrl", result.signUrl());
        }
        if (result.signParams() != null) {
            response.put("signParams", result.signParams());
        }

        return ResponseEntity.ok(response);
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

    // ========== T17.14: GET /{id}/status (跨端实时同步) ==========

    /**
     * 查询签署状态（跨端实时同步）。
     * <p>
     * 任意端查询同一合同 ID 返回一致的状态。
     * 签署中状态会主动同步远端 ESS 状态。
     *
     * @param id 合同 ID
     * @return 签署状态（含跨端同步信息）
     */
    @GetMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> getContractStatus(@PathVariable Long id) {
        ownershipGuard.requireAuthenticated();
        ownershipGuard.assertOwner(loadContractOrThrow(id).getUserId());

        SignStatusSyncService.SyncStatusResult syncResult = signStatusSyncService.getSyncedStatus(id);
        return ResponseEntity.ok(SignStatusSyncService.toResponseMap(syncResult));
    }

    private String requireParam(Map<String, String> request, String key) {
        String value = request.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("缺少必要参数: " + key);
        }
        return value;
    }

    /**
     * 解析客户端类型：优先从请求参数取，其次从请求头取，默认 H5。
     */
    private ClientType resolveClientType(Map<String, String> request, HttpServletRequest httpRequest) {
        String paramCt = request.get("clientType");
        if (paramCt != null && !paramCt.isBlank()) {
            return ClientTypeInterceptor.parseClientType(paramCt);
        }
        return ClientTypeInterceptor.resolve(httpRequest);
    }

    /**
     * 将 ClientType 映射到 SignSource。
     */
    private Contract.SignSource mapToSignSource(ClientType clientType) {
        return switch (clientType) {
            case H5 -> Contract.SignSource.H5;
            case MINI -> Contract.SignSource.MINI;
            case APP -> Contract.SignSource.APP;
        };
    }
}
