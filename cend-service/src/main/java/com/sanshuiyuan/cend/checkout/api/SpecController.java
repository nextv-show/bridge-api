package com.sanshuiyuan.cend.checkout.api;

import com.sanshuiyuan.cend.checkout.api.dto.SpecsResponse;
import com.sanshuiyuan.cend.checkout.application.SpecQueryService;
import com.sanshuiyuan.cend.common.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/h5/specs")
@Tag(name = "Specs")
public class SpecController {

    private final SpecQueryService specQueryService;

    public SpecController(SpecQueryService specQueryService) {
        this.specQueryService = specQueryService;
    }

    @GetMapping
    public ApiResponse<SpecsResponse> list() {
        return ApiResponse.ok(specQueryService.listActiveSpecs());
    }
}
