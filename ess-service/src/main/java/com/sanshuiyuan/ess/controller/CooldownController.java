package com.sanshuiyuan.ess.controller;

import com.sanshuiyuan.ess.service.CooldownService;
import com.sanshuiyuan.ess.service.CooldownService.CooldownStatusResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 冷静期控制器。
 * <p>
 * 提供冷静期状态查询端点。
 */
@RestController
@RequestMapping("/api/h5/contracts")
public class CooldownController {

    private static final Logger log = LoggerFactory.getLogger(CooldownController.class);

    private final CooldownService cooldownService;

    public CooldownController(CooldownService cooldownService) {
        this.cooldownService = cooldownService;
    }

    /**
     * 查询冷静期状态（含剩余时间计算）。
     *
     * @param id 合同 ID
     * @return 冷静期状态
     */
    @GetMapping("/{id}/cooldown-status")
    public ResponseEntity<Map<String, Object>> getCooldownStatus(@PathVariable Long id) {
        log.debug("查询冷静期状态 [contractId={}]", id);

        CooldownStatusResult result = cooldownService.getCooldownStatus(id);

        return ResponseEntity.ok(Map.of(
                "code", 0,
                "contractId", result.contractId(),
                "orderId", result.orderId(),
                "status", result.status(),
                "inCooldown", result.inCooldown(),
                "cooldownStartAt", result.cooldownStartAt() != null ? result.cooldownStartAt().toString() : "",
                "cooldownEndAt", result.cooldownEndAt() != null ? result.cooldownEndAt().toString() : "",
                "remainingSeconds", result.remainingSeconds(),
                "revokedAt", result.revokedAt() != null ? result.revokedAt().toString() : "",
                "revokeReason", result.revokeReason() != null ? result.revokeReason() : ""
        ));
    }
}
