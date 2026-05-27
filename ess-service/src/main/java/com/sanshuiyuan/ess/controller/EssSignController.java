package com.sanshuiyuan.ess.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.sanshuiyuan.ess.service.EssSignService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * ESS 签署端点。
 * <p>
 * 提供 H5、小程序、App 签署 URL/参数生成，以及企业自动签章。
 * 签署逻辑委托给 {@link EssSignService}。
 */
@RestController
@RequestMapping("/api/ess/sign")
public class EssSignController {

    private static final Logger log = LoggerFactory.getLogger(EssSignController.class);

    private final EssSignService signService;

    public EssSignController(EssSignService signService) {
        this.signService = signService;
    }

    // ========== T014: H5 签署 URL ==========

    /**
     * 生成 H5 签署 URL（直跳模式）。
     *
     * @param request 包含 contractId, signerId, jumpUrl
     * @return 签署 URL
     */
    @PostMapping("/h5-url")
    public ResponseEntity<Map<String, Object>> generateH5SignUrl(@RequestBody Map<String, String> request) {
        String contractId = requireParam(request, "contractId");
        String signerId = requireParam(request, "signerId");
        String jumpUrl = request.getOrDefault("jumpUrl", "");
        String h5Type = request.getOrDefault("h5Type", "jump");

        String signUrl = signService.generateH5SignUrl(contractId, signerId, jumpUrl, h5Type);

        return ResponseEntity.ok(Map.of(
                "code", 0,
                "signUrl", signUrl,
                "h5Type", h5Type
        ));
    }

    // ========== T015: 小程序签署参数 ==========

    /**
     * 生成小程序签署参数。
     *
     * @param request 包含 contractId, signerId, wxAppId (可选)
     * @return 小程序签署参数
     */
    @PostMapping("/miniapp-params")
    public ResponseEntity<Map<String, Object>> generateMiniAppSignParams(@RequestBody Map<String, String> request) {
        String contractId = requireParam(request, "contractId");
        String signerId = requireParam(request, "signerId");
        String wxAppId = request.get("wxAppId");

        JsonNode params = signService.generateMiniAppSignParams(contractId, signerId, wxAppId);

        return ResponseEntity.ok(Map.of(
                "code", 0,
                "signParams", params
        ));
    }

    // ========== T016: App 签署参数 ==========

    /**
     * 生成 App 签署参数（WebView/原生插件桥接）。
     *
     * @param request 包含 contractId, signerId, appType (android/ios)
     * @return App 签署参数
     */
    @PostMapping("/app-params")
    public ResponseEntity<Map<String, Object>> generateAppSignParams(@RequestBody Map<String, String> request) {
        String contractId = requireParam(request, "contractId");
        String signerId = requireParam(request, "signerId");
        String appType = requireParam(request, "appType");

        JsonNode params = signService.generateAppSignParams(contractId, signerId, appType);

        return ResponseEntity.ok(Map.of(
                "code", 0,
                "signParams", params
        ));
    }

    /**
     * 企业自动签章。
     */
    @PostMapping("/server-sign")
    public ResponseEntity<Map<String, Object>> createServerSign(@RequestBody Map<String, String> request) {
        String contractId = requireParam(request, "contractId");
        signService.createServerSign(contractId);

        return ResponseEntity.ok(Map.of(
                "code", 0,
                "message", "企业自动签章完成"
        ));
    }

    private String requireParam(Map<String, String> request, String key) {
        String value = request.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("缺少必要参数: " + key);
        }
        return value;
    }
}
