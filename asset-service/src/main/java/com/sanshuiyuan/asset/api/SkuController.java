package com.sanshuiyuan.asset.api;

import com.sanshuiyuan.asset.domain.Sku;
import com.sanshuiyuan.asset.domain.SkuStatus;
import com.sanshuiyuan.asset.infra.repository.SkuRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "SKU 接口")
@RestController
@RequestMapping("/skus")
public class SkuController {

    private final SkuRepository skuRepository;

    public SkuController(SkuRepository skuRepository) {
        this.skuRepository = skuRepository;
    }

    @Operation(summary = "获取 SKU 列表")
    @GetMapping
    public List<Sku> listSkus(@RequestParam(defaultValue = "ACTIVE") SkuStatus status) {
        return skuRepository.findByStatus(status);
    }

    @Operation(summary = "获取 SKU 详情")
    @GetMapping("/{id}")
    public ResponseEntity<Sku> getSku(@PathVariable Long id) {
        return skuRepository.findByIdAndStatus(id, SkuStatus.ACTIVE)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
