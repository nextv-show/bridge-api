package com.sanshuiyuan.admin.api;

import com.sanshuiyuan.admin.api.dto.SkuRequest;
import com.sanshuiyuan.admin.domain.Sku;
import com.sanshuiyuan.admin.infra.repository.SkuRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
    public List<Sku> list() {
        return skuRepo.findAll();
    }

    @GetMapping("/{id}")
    public Sku get(@PathVariable Long id) {
        return skuRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public Sku create(@RequestBody SkuRequest req) {
        Sku sku = new Sku();
        sku.setName(req.name());
        sku.setPriceCents(req.priceCents());
        sku.setDepositCents(req.depositCents() != null ? req.depositCents() : 0L);
        sku.setBenefitsMd(req.benefitsMd());
        sku.setImageUrl(req.imageUrl());
        return skuRepo.save(sku);
    }

    @PutMapping("/{id}")
    public Sku update(@PathVariable Long id, @RequestBody SkuRequest req) {
        Sku sku = skuRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        sku.setName(req.name());
        sku.setPriceCents(req.priceCents());
        sku.setDepositCents(req.depositCents() != null ? req.depositCents() : 0L);
        sku.setBenefitsMd(req.benefitsMd());
        sku.setImageUrl(req.imageUrl());
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
}
