package com.sanshuiyuan.admin.api;

import com.sanshuiyuan.admin.api.dto.SkuRequest;
import com.sanshuiyuan.admin.domain.Sku;
import com.sanshuiyuan.admin.infra.repository.SkuRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/skus")
public class SkuAdminController {

    private final SkuRepository skuRepo;

    public SkuAdminController(SkuRepository skuRepo) {
        this.skuRepo = skuRepo;
    }

    @GetMapping
    public Map<String, Object> list() {
        List<Sku> items = skuRepo.findAll();
        return Map.of("items", items, "total", items.size());
    }

    @GetMapping("/{id}")
    public Sku get(@PathVariable Long id) {
        return skuRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public Sku create(@RequestBody SkuRequest req) {
        Sku sku = new Sku();
        applyToEntity(sku, req);
        return skuRepo.save(sku);
    }

    @PutMapping("/{id}")
    public Sku update(@PathVariable Long id, @RequestBody SkuRequest req) {
        Sku sku = skuRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        applyToEntity(sku, req);
        return skuRepo.save(sku);
    }

    @PutMapping("/{id}/status")
    public Map<String, String> toggleStatus(@PathVariable Long id,
                                            @RequestBody Map<String, String> body) {
        Sku sku = skuRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        sku.setStatus(Sku.Status.valueOf(body.get("status")));
        skuRepo.save(sku);
        return Map.of("status", sku.getStatus().name());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        Sku sku = skuRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        skuRepo.delete(sku);
    }

    private void applyToEntity(Sku sku, SkuRequest req) {
        if (req.name() != null) sku.setName(req.name());
        if (req.code() != null) sku.setCode(req.code());
        if (req.subtitle() != null) sku.setSubtitle(req.subtitle());
        if (req.category() != null) sku.setCategory(req.category());
        if (req.priceCents() != null) sku.setPriceCents(req.priceCents());
        if (req.originalCents() != null) sku.setOriginalCents(req.originalCents());
        if (req.costCents() != null) sku.setCostCents(req.costCents());
        if (req.depositCents() != null) sku.setDepositCents(req.depositCents());
        if (req.stock() != null) {
            sku.setStock(req.stock());
            sku.setLowStock(req.stockWarn() != null && req.stock() <= req.stockWarn());
        }
        if (req.stockWarn() != null) sku.setStockWarn(req.stockWarn());
        if (req.s1Months() != null) sku.setS1Months(req.s1Months());
        if (req.s2Months() != null) sku.setS2Months(req.s2Months());
        if (req.fuseAt() != null) sku.setFuseAt(req.fuseAt());
        if (req.annualizedBp() != null) sku.setAnnualizedBp(req.annualizedBp());
        if (req.accent() != null) sku.setAccent(req.accent());
        if (req.featured() != null) sku.setFeatured(req.featured());
        if (req.note() != null) sku.setNote(req.note());
        if (req.benefitsMd() != null) sku.setBenefitsMd(req.benefitsMd());
        if (req.imageUrl() != null) sku.setImageUrl(req.imageUrl());
        if (req.status() != null) sku.setStatus(Sku.Status.valueOf(req.status()));
    }
}
