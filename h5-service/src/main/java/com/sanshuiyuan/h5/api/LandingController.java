package com.sanshuiyuan.h5.api;

import com.sanshuiyuan.h5.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * P1 落地页配置只读接口。
 * Phase A：返回脚手架桩，验证服务可启动 + 统一响应链路打通；
 * Phase B 接入 LandingConfigService 返回真实 PUBLISHED 配置。
 */
@RestController
@RequestMapping("/api/h5/landing")
@Tag(name = "Landing", description = "P1 资产预售首页落地页配置")
public class LandingController {

    @Operation(summary = "获取落地页配置（脚手架桩）")
    @GetMapping("/config")
    public ApiResponse<String> getConfig() {
        return ApiResponse.ok("h5-service scaffold ready");
    }
}
