package com.sanshuiyuan.settlement.api;

import com.sanshuiyuan.settlement.auth.SettlementSubjectResolver;
import com.sanshuiyuan.settlement.domain.DeviceStage;
import com.sanshuiyuan.settlement.infra.asset.DeviceAssetRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运营设备列表 API：列出当前 owner 名下全部设备资产（core_db.device_assets）。
 *
 * 背景：设备资产真身在 settlement 的 core_db.device_assets，而 asset-service 的 asset_db.device_assets
 * 长期为空；小程序原先只读 asset-service `/assets/mine` + matching 待匹配兜底，运营中（STAGE_1/STAGE_2）
 * 设备没有任何数据通路，导致运营设备主在小程序里被判成纯用水用户（看不到「设备运营」）。本端点补齐该通路。
 *
 * 鉴权：复用 H5 JWT（subject=openid/unionid，经 SettlementSubjectResolver 解析为 users.id），与
 * `/api/s/owner/wallet` 同一身份链。返回 DTO 形状与小程序 services/assets.ts 的 AssetDTO（camelCase）对齐。
 */
@RestController
@RequestMapping("/api/s")
public class OwnerAssetController {

    private final DeviceAssetRepository deviceAssetRepository;
    private final SettlementSubjectResolver subjectResolver;

    public OwnerAssetController(DeviceAssetRepository deviceAssetRepository,
                                SettlementSubjectResolver subjectResolver) {
        this.deviceAssetRepository = deviceAssetRepository;
        this.subjectResolver = subjectResolver;
    }

    @GetMapping("/owner/assets")
    public ResponseEntity<Map<String, Object>> getMyAssets(Authentication auth) {
        Long userId = subjectResolver.resolveUserId(auth.getName());
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "UNAUTHORIZED"));
        }

        List<Map<String, Object>> data = deviceAssetRepository.findByUserIdOrderByIdAsc(userId).stream()
                .map(a -> {
                    boolean pendingMatch = a.getStage() == DeviceStage.PENDING_MATCH;
                    Integer roiBp = a.getRoiBp();
                    // 与 asset-service AssetDto 一致：待撮合不下发收益；fused 由 roi 达上限推导。
                    boolean fused = !pendingMatch && roiBp != null && roiBp >= 20000;
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", a.getId());
                    m.put("sn", a.getSn());
                    m.put("model", a.getModel());
                    m.put("stage", a.getStage().name());
                    m.put("cumulativeIncomeCents", pendingMatch ? null : a.getCumulativeIncomeCents());
                    m.put("roiBp", pendingMatch ? null : roiBp);
                    m.put("pendingMatch", pendingMatch);
                    m.put("fused", fused);
                    return m;
                })
                .toList();

        return ResponseEntity.ok(Map.of("code", 0, "data", data));
    }
}
