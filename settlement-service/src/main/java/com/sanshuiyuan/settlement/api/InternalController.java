package com.sanshuiyuan.settlement.api;

import com.sanshuiyuan.settlement.application.reconciliation.ManualAdjustUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 内部接口（S2S 鉴权），供 005-admin 调用。
 */
@RestController
@RequestMapping("/api/s/internal")
public class InternalController {

    private final ManualAdjustUseCase manualAdjustUseCase;

    public InternalController(ManualAdjustUseCase manualAdjustUseCase) {
        this.manualAdjustUseCase = manualAdjustUseCase;
    }

    @PostMapping("/manual-adjust")
    public ResponseEntity<Map<String, Object>> manualAdjust(@RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> result = manualAdjustUseCase.adjust(body);
            return ResponseEntity.ok(Map.of("code", 0, "data", result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(422).body(Map.of("code", 422, "message", e.getMessage()));
        }
    }
}
