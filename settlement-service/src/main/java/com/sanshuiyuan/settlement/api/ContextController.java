package com.sanshuiyuan.settlement.api;

import com.sanshuiyuan.settlement.application.context.ContextModeResolver;
import com.sanshuiyuan.settlement.application.context.ContextModeResolver.AssetSummary;
import com.sanshuiyuan.settlement.auth.SettlementSubjectResolver;
import com.sanshuiyuan.settlement.infra.asset.DeviceAssetRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用户上下文聚合 API：返回小程序首页/个人中心分流所需的 userMode / primaryTask / 设备资产汇总。
 *
 * 决策（2026-06-18）：userMode 数据源由「前端聚合多源」改为「后端轻量上下文接口」，根治运营设备主
 * 被误判为 WATER_USER（详见 ContextModeResolver 注释）。鉴权复用 H5 JWT，与 /api/s/owner/assets 同身份链。
 *
 * V1 边界：mode 仅由本地 core_db.device_assets 解析（运营优先三态），不做跨服务扇出；MIXED 由前端用
 * 本地水费/约水信号在收到本结果后升级。响应 camelCase，对齐前端 services/userContext.ts。
 */
@RestController
@RequestMapping("/api/s")
public class ContextController {

    private final DeviceAssetRepository deviceAssetRepository;
    private final SettlementSubjectResolver subjectResolver;

    public ContextController(DeviceAssetRepository deviceAssetRepository,
                             SettlementSubjectResolver subjectResolver) {
        this.deviceAssetRepository = deviceAssetRepository;
        this.subjectResolver = subjectResolver;
    }

    @GetMapping("/context")
    public ResponseEntity<Map<String, Object>> getContext(Authentication auth) {
        Long userId = subjectResolver.resolveUserId(auth.getName());
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "UNAUTHORIZED"));
        }

        AssetSummary summary = ContextModeResolver.summarize(
                deviceAssetRepository.findByUserIdOrderByIdAsc(userId));
        String mode = ContextModeResolver.resolveMode(summary);
        String primaryTask = ContextModeResolver.resolvePrimaryTask(mode);

        Map<String, Object> assetSummary = new LinkedHashMap<>();
        assetSummary.put("total", summary.total());
        assetSummary.put("pendingMatch", summary.pendingMatch());
        assetSummary.put("pendingActivate", summary.pendingActivate());
        assetSummary.put("active", summary.active());
        assetSummary.put("offline", summary.offline());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId);
        data.put("mode", mode);
        data.put("primaryTask", primaryTask);
        data.put("assetSummary", assetSummary);

        return ResponseEntity.ok(Map.of("code", 0, "data", data));
    }
}
