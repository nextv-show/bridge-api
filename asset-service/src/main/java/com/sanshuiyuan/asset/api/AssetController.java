package com.sanshuiyuan.asset.api;

import com.sanshuiyuan.asset.api.dto.AssetDto;
import com.sanshuiyuan.asset.application.AssetQueryService;
import com.sanshuiyuan.asset.domain.DeviceAsset;
import com.sanshuiyuan.asset.domain.Stage;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/assets")
public class AssetController {

    private final AssetQueryService assetQueryService;

    public AssetController(AssetQueryService assetQueryService) {
        this.assetQueryService = assetQueryService;
    }

    @GetMapping("/mine")
    public ResponseEntity<List<AssetDto>> getMyAssets(
            @AuthenticationPrincipal Long userId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size) {

        List<AssetDto> dtos = assetQueryService.getMyAssets(userId, page, size)
                .getContent().stream()
                .map(this::mapToDto)
                .toList();

        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{sn}")
    public ResponseEntity<AssetDto> getAssetBySn(
            @AuthenticationPrincipal Long userId,
            @PathVariable String sn) {

        return assetQueryService.getOwnedAsset(userId, sn)
                .map(asset -> ResponseEntity.ok(mapToDto(asset)))
                .orElse(ResponseEntity.notFound().build());
    }

    private AssetDto mapToDto(DeviceAsset asset) {
        boolean pendingMatch = asset.getStage() == Stage.PENDING_MATCH;
        // FR-5.3 / D.3.3: SN 未绑定（待撮合）时不下发收益字段，DTO 显式置空
        Long incomeCents = pendingMatch ? null : asset.getCumulativeIncomeCents();
        Integer roiBp = pendingMatch ? null : asset.getRoiBp();
        boolean fused = !pendingMatch && asset.getRoiBp() >= 20000;
        return new AssetDto(
                asset.getId(),
                asset.getSn(),
                asset.getModel(),
                asset.getPurchasedAt(),
                asset.getStage(),
                incomeCents,
                roiBp,
                pendingMatch,
                fused
        );
    }
}
