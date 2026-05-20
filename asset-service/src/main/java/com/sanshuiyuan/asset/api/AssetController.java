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
        return new AssetDto(
                asset.getId(),
                asset.getSn(),
                asset.getModel(),
                asset.getPurchasedAt(),
                asset.getStage(),
                asset.getCumulativeIncomeCents(),
                asset.getRoiBp(),
                asset.getStage() == Stage.PENDING_MATCH,
                asset.getRoiBp() >= 20000
        );
    }
}
