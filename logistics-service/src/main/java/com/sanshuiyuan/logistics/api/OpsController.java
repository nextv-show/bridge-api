package com.sanshuiyuan.logistics.api;

import com.sanshuiyuan.logistics.application.AdvanceStateUseCase;
import com.sanshuiyuan.logistics.domain.LogisticsStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * D.3.2 Ops 临时人工推进（005 上线后下线）。
 * 需 admin 角色 + service-to-service token 双保险（SecurityConfig 控制）。
 */
@RestController
@RequestMapping("/logistics/ops")
public class OpsController {

    private final AdvanceStateUseCase advanceStateUseCase;

    public OpsController(AdvanceStateUseCase advanceStateUseCase) {
        this.advanceStateUseCase = advanceStateUseCase;
    }

    @PostMapping("/{id}/advance")
    public ResponseEntity<Map<String, Object>> advance(
            @PathVariable long id,
            @RequestBody AdvanceBody body) {
        var order = advanceStateUseCase.advance(id, body.toStatus, body.note, null);
        return ResponseEntity.ok(Map.of(
                "id", order.getId(),
                "status", order.getStatus().name(),
                "request_id", order.getRequestId()
        ));
    }

    public record AdvanceBody(LogisticsStatus toStatus, String note) {}
}
