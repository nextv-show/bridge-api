package com.sanshuiyuan.water.settlement.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 接收 004-settlement-engine 的 STAGE_CHANGED 日志型事件。
 * V1 仅落日志，不触发任何业务动作（device_permissions 由控制面独立管理）。
 */
@RestController
@RequestMapping("/internal/settlement")
public class InternalSettlementController {
    private static final Logger log = LoggerFactory.getLogger(InternalSettlementController.class);

    @PostMapping("/stage-changed")
    public ResponseEntity<Map<String, Object>> onStageChanged(@RequestBody Map<String, Object> payload) {
        String sn = (String) payload.getOrDefault("sn", "unknown");
        String fromStage = (String) payload.getOrDefault("from_stage", "unknown");
        String toStage = (String) payload.getOrDefault("to_stage", "unknown");
        Object atBillId = payload.get("at_bill_id");
        Object atRoiBp = payload.get("at_roi_bp");

        log.info("[stage-changed] SN={} {}→{} billId={} roiBp={}", sn, fromStage, toStage, atBillId, atRoiBp);

        return ResponseEntity.ok(Map.of("code", 0));
    }
}
