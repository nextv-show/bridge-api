package com.sanshuiyuan.admin.api;

import com.sanshuiyuan.admin.api.dto.BindSnRequest;
import com.sanshuiyuan.admin.application.BindSnUseCase;
import com.sanshuiyuan.admin.domain.DeviceAsset;
import com.sanshuiyuan.admin.infra.repository.DeviceAssetRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin/devices")
public class DeviceAdminController {

    private final DeviceAssetRepository deviceRepo;
    private final BindSnUseCase bindSnUseCase;

    public DeviceAdminController(DeviceAssetRepository deviceRepo, BindSnUseCase bindSnUseCase) {
        this.deviceRepo = deviceRepo;
        this.bindSnUseCase = bindSnUseCase;
    }

    @GetMapping
    public Page<Map<String, Object>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) Boolean unbound) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<DeviceAsset> devices;
        if (Boolean.TRUE.equals(unbound)) {
            devices = deviceRepo.findBySnIsNullAndStage(
                    DeviceAsset.Stage.PENDING_MATCH, pageable);
        } else {
            devices = deviceRepo.findAll(pageable);
        }
        return devices.map(this::toDto);
    }

    @PutMapping("/{id}/bind-sn")
    public Map<String, String> bindSn(@PathVariable Long id,
                                      @RequestBody BindSnRequest req,
                                      Authentication auth) {
        Long adminId = (Long) auth.getPrincipal();
        bindSnUseCase.bindSn(adminId, id, req.sn());
        return Map.of("result", "ok");
    }

    private Map<String, Object> toDto(DeviceAsset d) {
        return Map.of(
            "id", d.getId(),
            "userId", d.getUserId(),
            "orderId", d.getOrderId(),
            "sn", d.getSn() != null ? d.getSn() : "",
            "model", d.getModel(),
            "stage", d.getStage().name(),
            "purchasedAt", d.getPurchasedAt().toString()
        );
    }
}
