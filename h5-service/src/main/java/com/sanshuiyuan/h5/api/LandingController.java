package com.sanshuiyuan.h5.api;

import com.sanshuiyuan.h5.api.dto.LandingConfigResponse;
import com.sanshuiyuan.h5.application.LandingConfigService;
import com.sanshuiyuan.h5.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * P1 落地页配置只读接口。公开（无鉴权，决策门 G-8）、限频 120/min/IP（{@link com.sanshuiyuan.h5.config.RateLimitConfig}）。
 */
@RestController
@RequestMapping("/api/h5/landing")
@Tag(name = "Landing", description = "P1 资产预售首页落地页配置")
public class LandingController {

    private final LandingConfigService landingConfigService;

    public LandingController(LandingConfigService landingConfigService) {
        this.landingConfigService = landingConfigService;
    }

    @Operation(summary = "获取当前生效（PUBLISHED）落地页配置",
            description = "返回 hero/features/simulator/trustBadges/footer。无生效配置返回 503 NO_ACTIVE_CONFIG，前端回退内置默认文案。")
    @GetMapping("/config")
    public ApiResponse<LandingConfigResponse> getConfig() {
        return ApiResponse.ok(landingConfigService.getPublishedConfig());
    }
}
