package com.sanshuiyuan.cend.checkout.api;

import com.sanshuiyuan.cend.checkout.api.dto.AssetDto;
import com.sanshuiyuan.cend.checkout.application.AssetQueryService;
import com.sanshuiyuan.cend.auth.CurrentOpenid;
import com.sanshuiyuan.cend.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/c/order")
@Tag(name = "Asset")
public class OrderAssetController {

    private final AssetQueryService assetQueryService;

    public OrderAssetController(AssetQueryService assetQueryService) {
        this.assetQueryService = assetQueryService;
    }

    @GetMapping("/{id}/asset")
    @Operation(summary = "查询订单资产确权信息")
    public ApiResponse<AssetDto> getAsset(@PathVariable Long id) {
        String openid = CurrentOpenid.require();
        return ApiResponse.ok(assetQueryService.queryAsset(id, openid));
    }
}
