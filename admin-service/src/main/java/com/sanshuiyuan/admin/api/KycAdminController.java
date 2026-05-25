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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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

    /**
     * 分页列表。page 为 0-based（与 Spring Data 对齐），前端按 0-based 传参。
     * 返回 {items,total,page,size}，字段名与前端 KYCRecord 契约一致。
     */
    @GetMapping("/records")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<KycRecord> records = status != null && !status.isBlank()
                ? kycRepo.findByStatus(KycRecord.Status.valueOf(status), pageable)
                : kycRepo.findAllByOrderByCreatedAtDesc(pageable);

        List<Map<String, Object>> items = records.getContent().stream()
                .map(this::toDto)
                .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("total", records.getTotalElements());
        body.put("page", page);
        body.put("size", size);
        return body;
    }

    /** 各状态记录数 — 供队列概览条展示真实总量（替代前端按当前页客户端计数）。 */
    @GetMapping("/records/counts")
    public Map<String, Long> counts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (KycRecord.Status s : KycRecord.Status.values()) {
            counts.put(s.name(), kycRepo.countByStatus(s));
        }
        return counts;
    }

    @PostMapping("/records/{id}/approve")
    public Map<String, String> approve(@PathVariable Long id, Authentication auth) {
        Long adminId = (Long) auth.getPrincipal();
        kycReview.approve(adminId, id);
        return Map.of("status", "PASS");
    }

    @PostMapping("/records/{id}/reject")
    public Map<String, String> reject(@PathVariable Long id,
                                      @RequestBody RejectRequest req,
                                      Authentication auth) {
        Long adminId = (Long) auth.getPrincipal();
        kycReview.reject(adminId, id, req != null ? req.reason() : null);
        return Map.of("status", "REJECT");
    }

    /** 驳回请求体 — 前端 RejectModal 提交 {reason}。 */
    public record RejectRequest(String reason) {}

    /**
     * 脱敏 DTO，字段名与前端 KYCRecord 契约一致。
     * 真实姓名/身份证由 h5-service 加密存储，admin 仅返回脱敏值。
     * 设计稿中的证件照/人脸分/年龄性别/尝试次数无数据源（阿里云金融级认证托管，
     * 商户侧不留存），故不返回，前端以合规占位展示。
     */
    private Map<String, Object> toDto(KycRecord r) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", r.getId());
        dto.put("openid", r.getOpenid());
        dto.put("realNameMask", r.getRealNameMask() != null ? r.getRealNameMask() : "***");
        dto.put("idCardNoMask", r.getIdCardNoMask());
        dto.put("channel", r.getChannel() != null ? r.getChannel() : "");
        dto.put("certifyId", r.getCertifyId() != null ? r.getCertifyId() : "");
        dto.put("status", r.getStatus().name());
        dto.put("verifiedAt", r.getVerifiedAt() != null ? r.getVerifiedAt().toString() : null);
        dto.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
        dto.put("rejectReason", r.getRejectReason());
        return dto;
    }
}
