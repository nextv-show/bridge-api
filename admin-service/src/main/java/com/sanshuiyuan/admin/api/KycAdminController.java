package com.sanshuiyuan.admin.api;

import com.sanshuiyuan.admin.application.KycReviewService;
import com.sanshuiyuan.admin.domain.KycRecord;
import com.sanshuiyuan.admin.infra.repository.KycRecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin/kyc")
public class KycAdminController {

    private final KycRecordRepository kycRepo;
    private final KycReviewService kycReview;

    public KycAdminController(KycRecordRepository kycRepo, KycReviewService kycReview) {
        this.kycRepo = kycRepo;
        this.kycReview = kycReview;
    }

    @GetMapping("/records")
    public Page<Map<String, Object>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<KycRecord> records = status != null
                ? kycRepo.findByStatus(KycRecord.Status.valueOf(status), pageable)
                : kycRepo.findAllByOrderByCreatedAtDesc(pageable);
        return records.map(this::toDto);
    }

    @PostMapping("/records/{id}/approve")
    public Map<String, String> approve(@PathVariable Long id, Authentication auth) {
        Long adminId = (Long) auth.getPrincipal();
        kycReview.approve(adminId, id);
        return Map.of("status", "PASS");
    }

    @PostMapping("/records/{id}/reject")
    public Map<String, String> reject(@PathVariable Long id, Authentication auth) {
        Long adminId = (Long) auth.getPrincipal();
        kycReview.reject(adminId, id);
        return Map.of("status", "REJECT");
    }

    /** 返回脱敏数据 — 真实姓名和身份证号由 h5-service 加密存储 */
    private Map<String, Object> toDto(KycRecord r) {
        return Map.of(
            "id", r.getId(),
            "openid", r.getOpenid(),
            "realName", r.getRealNameMask() != null ? r.getRealNameMask() : "***",
            "idNumber", r.getIdCardNoMask(),
            "channel", r.getChannel() != null ? r.getChannel() : "",
            "certifyId", r.getCertifyId() != null ? r.getCertifyId() : "",
            "status", r.getStatus().name(),
            "verifiedAt", r.getVerifiedAt() != null ? r.getVerifiedAt().toString() : "",
            "createdAt", r.getCreatedAt().toString()
        );
    }
}
