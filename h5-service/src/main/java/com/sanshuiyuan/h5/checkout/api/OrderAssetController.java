package com.sanshuiyuan.h5.checkout.api;

import com.sanshuiyuan.h5.checkout.api.dto.AssetDto;
import com.sanshuiyuan.h5.checkout.application.AssetQueryService;
import com.sanshuiyuan.h5.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/h5/order")
@Tag(name = "Asset")
public class OrderAssetController {

    private final AssetQueryService assetQueryService;

    public OrderAssetController(AssetQueryService assetQueryService) {
        this.assetQueryService = assetQueryService;
    }

    @GetMapping("/{id}/asset")
    @Operation(summary = "查询订单资产确权信息")
    public ApiResponse<AssetDto> getAsset(@PathVariable Long id) {
        // TODO: extract openid from JWT once auth is wired
        String openid = "stub-openid";
        return ApiResponse.ok(assetQueryService.queryAsset(id, openid));
    }
}
