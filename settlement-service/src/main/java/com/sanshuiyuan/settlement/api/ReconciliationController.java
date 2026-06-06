package com.sanshuiyuan.settlement.api;

import com.sanshuiyuan.settlement.domain.ReconciliationAlert;
import com.sanshuiyuan.settlement.domain.ReconciliationStatus;
import com.sanshuiyuan.settlement.infra.repository.ReconciliationAlertRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

/**
 * 对账查询接口（plan §5.1，SVC token 鉴权）。供 005-admin 查看每日对账结果与未处理告警。
 */
@RestController
@RequestMapping("/api/s")
public class ReconciliationController {

    private final ReconciliationAlertRepository alertRepository;

    public ReconciliationController(ReconciliationAlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    @GetMapping("/reconciliation/daily")
    public ResponseEntity<Map<String, Object>> getDaily(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        ReconciliationAlert alert = alertRepository.findByDate(date).orElse(null);
        if (alert == null) {
            return ResponseEntity.ok(Map.of("code", 0, "data", Map.of("date", date.toString(), "status", "NOT_RUN")));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("date", alert.getDate().toString());
        data.put("diff_cents", alert.getDiffCents());
        data.put("status", alert.getStatus().name());
        data.put("payload_json", alert.getPayloadJson());
        data.put("created_at", alert.getCreatedAt() != null ? alert.getCreatedAt().toString() : null);
        data.put("resolved_at", alert.getResolvedAt() != null ? alert.getResolvedAt().toString() : null);
        return ResponseEntity.ok(Map.of("code", 0, "data", data));
    }

    @GetMapping("/reconciliation/alerts")
    public ResponseEntity<Map<String, Object>> listAlerts(
            @RequestParam(defaultValue = "OPEN") String status) {
        List<ReconciliationAlert> alerts = alertRepository.findByStatusOrderByDateDesc(
                ReconciliationStatus.valueOf(status));
        List<Map<String, Object>> items = alerts.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", a.getDate().toString());
            m.put("diff_cents", a.getDiffCents());
            m.put("status", a.getStatus().name());
            m.put("created_at", a.getCreatedAt() != null ? a.getCreatedAt().toString() : null);
            return m;
        }).toList();
        return ResponseEntity.ok(Map.of("code", 0, "data", items));
    }
}
