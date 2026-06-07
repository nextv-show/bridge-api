package com.sanshuiyuan.settlement.api;

import com.sanshuiyuan.settlement.domain.SettlementEntry;
import com.sanshuiyuan.settlement.infra.asset.DeviceAssetRepository;
import com.sanshuiyuan.settlement.infra.repository.SettlementEntryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 分账查询 API：JWT owner 仅能查看自己名下设备的分账明细。 */
@RestController
@RequestMapping("/api/s")
public class SettlementController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SettlementController.class);

    private final SettlementEntryRepository settlementEntryRepository;
    private final DeviceAssetRepository deviceAssetRepository;

    public SettlementController(SettlementEntryRepository settlementEntryRepository,
                                 DeviceAssetRepository deviceAssetRepository) {
        this.settlementEntryRepository = settlementEntryRepository;
        this.deviceAssetRepository = deviceAssetRepository;
    }

    /**
     * B.4.1: 按 bill_id 查分账明细。JWT owner 仅能查自己的 SN 的账单。
     */
    @GetMapping("/settlements/by-bill/{billId}")
    public ResponseEntity<Map<String, Object>> getByBill(@PathVariable Long billId, Authentication auth) {
        List<SettlementEntry> entries = settlementEntryRepository.findByBillId(billId);
        if (entries.isEmpty()) {
            return ResponseEntity.ok(Map.of("code", 0, "data", List.of()));
        }
        // 鉴权：JWT subject 必须是该 SN 的所有权人（subject = user_id）
        String sn = entries.get(0).getSn();
        Long subjectUserId = parseSubject(auth);
        if (subjectUserId == null
                || deviceAssetRepository.findBySnAndUserId(sn, subjectUserId).isEmpty()) {
            return ResponseEntity.status(403).body(Map.of("code", 403, "message", "Not the owner of this device"));
        }

        List<Map<String, Object>> data = entries.stream().map(e -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("bill_id", e.getBillId());
            m.put("sn", e.getSn());
            m.put("beneficiary_type", e.getBeneficiaryType().name());
            m.put("beneficiary_user_id", e.getBeneficiaryUserId());
            m.put("amount_cents", e.getAmountCents());
            m.put("owner_bp", e.getOwnerBp());
            m.put("promoter_bp", e.getPromoterBp());
            m.put("platform_bp", e.getPlatformBp());
            m.put("split_reason", e.getSplitReason().name());
            m.put("stage_at_post", e.getStageAtPost().name());
            m.put("posted_at", e.getPostedAt() != null ? e.getPostedAt().toString() : null);
            return m;
        }).toList();
        return ResponseEntity.ok(Map.of("code", 0, "data", data));
    }

    /**
     * B.4.2: 按 SN 查分账历史 + 设备累计收益、ROI、阶段。
     */
    @GetMapping("/settlements/by-device/{sn}")
    public ResponseEntity<Map<String, Object>> getByDevice(@PathVariable String sn,
                                                            @RequestParam(required = false) String from,
                                                            @RequestParam(required = false) String to,
                                                            Authentication auth) {
        var asset = deviceAssetRepository.findBySn(sn);
        if (asset.isEmpty()) {
            return ResponseEntity.ok(Map.of("code", 404, "message", "Device not found"));
        }

        // 鉴权
        Long subjectUserId = parseSubject(auth);
        if (subjectUserId == null || !asset.get().getUserId().equals(subjectUserId)) {
            return ResponseEntity.status(403).body(Map.of("code", 403, "message", "Not the owner of this device"));
        }

        LocalDateTime fromTime = from != null ? LocalDateTime.parse(from) : LocalDateTime.now().minusYears(1);
        LocalDateTime toTime = to != null ? LocalDateTime.parse(to) : LocalDateTime.now();

        List<SettlementEntry> entries = settlementEntryRepository.findBySnAndPostedAtBetween(sn, fromTime, toTime);

        List<Map<String, Object>> data = entries.stream().map(e -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("bill_id", e.getBillId());
            m.put("beneficiary_type", e.getBeneficiaryType().name());
            m.put("amount_cents", e.getAmountCents());
            m.put("owner_bp", e.getOwnerBp());
            m.put("split_reason", e.getSplitReason().name());
            m.put("posted_at", e.getPostedAt() != null ? e.getPostedAt().toString() : null);
            return m;
        }).toList();

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("entries", data);
        result.put("cumulative_income_cents", asset.get().getCumulativeIncomeCents());
        result.put("roi_bp", asset.get().getRoiBp());
        result.put("stage", asset.get().getStage().name());

        return ResponseEntity.ok(Map.of("code", 0, "data", result));
    }

    /** JWT subject 解析为 user_id；缺失或非数字返回 null（视为未授权）。 */
    private Long parseSubject(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            return null;
        }
        try {
            return Long.parseLong(auth.getName());
        } catch (NumberFormatException e) {
            log.warn("JWT subject {} is not a numeric user_id", auth.getName());
            return null;
        }
    }
}
