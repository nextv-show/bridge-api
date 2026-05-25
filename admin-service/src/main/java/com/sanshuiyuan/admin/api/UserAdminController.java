package com.sanshuiyuan.admin.api;

import com.sanshuiyuan.admin.api.dto.UserUpsertRequest;
import com.sanshuiyuan.admin.application.AdminUserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * C 端用户管理 API。
 * users 表为 admin-service 在 asset_db 拥有的去规范化表；订单数/GMV/设备数实时聚合。
 */
@RestController
@RequestMapping("/admin/users")
public class UserAdminController {

    private final AdminUserService userService;

    public UserAdminController(AdminUserService userService) {
        this.userService = userService;
    }

    /** 分页列表（page 0-based），返回 {items,total,page,size}。 */
    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "ALL") String tab,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String tier,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "last_active") String sort) {
        return userService.list(page, size, tab, channel, tier, q, sort);
    }

    /** 各 tab 计数 — {ALL,KYC_PASS,KYC_PEND,RISK,FROZEN}。 */
    @GetMapping("/counts")
    public Map<String, Long> counts() {
        return userService.counts();
    }

    /** 用户详情（含订单/设备/地址列表）。 */
    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        return userService.detail(id);
    }

    /** 新建用户（仅 SUPER_ADMIN）。 */
    @PostMapping
    public Map<String, Object> create(@RequestBody UserUpsertRequest req, Authentication auth) {
        Long adminId = (Long) auth.getPrincipal();
        return userService.create(resolveRole(auth), req, adminId, resolveOperator(auth));
    }

    /** 从 user-service 同步真实用户目录到 admin users 表（仅 SUPER_ADMIN）。 */
    @PostMapping("/sync")
    public Map<String, Object> sync(Authentication auth) {
        if (!"SUPER_ADMIN".equals(resolveRole(auth))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅超级管理员可同步用户");
        }
        Long adminId = (Long) auth.getPrincipal();
        return userService.syncFromUserService(adminId, resolveOperator(auth));
    }

    /** 编辑用户。 */
    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id,
                                      @RequestBody UserUpsertRequest req,
                                      Authentication auth) {
        Long adminId = (Long) auth.getPrincipal();
        return userService.update(id, req, adminId);
    }

    /** 冻结用户。 */
    @PostMapping("/{id}/freeze")
    public Map<String, String> freeze(@PathVariable Long id,
                                      @RequestBody FreezeRequest req,
                                      Authentication auth) {
        Long adminId = (Long) auth.getPrincipal();
        userService.freeze(id, req != null ? req.reason() : null,
                req != null ? req.duration() : null, adminId, resolveOperator(auth));
        return Map.of("status", "FROZEN");
    }

    /** 解冻用户。 */
    @PostMapping("/{id}/unfreeze")
    public Map<String, String> unfreeze(@PathVariable Long id,
                                        @RequestBody(required = false) UnfreezeRequest req,
                                        Authentication auth) {
        Long adminId = (Long) auth.getPrincipal();
        userService.unfreeze(id, req != null ? req.reason() : null, adminId, resolveOperator(auth));
        return Map.of("status", "ACTIVE");
    }

    /** 从 JWT 授权中取角色，去掉 ROLE_ 前缀。 */
    private String resolveRole(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
                .orElse("");
    }

    /**
     * 解析操作人显示名：优先 JWT username 声明（AdminJwtFilter 写入 details），
     * 否则回退到 getName()（即 adminId 字符串）。
     */
    private String resolveOperator(Authentication auth) {
        Object details = auth.getDetails();
        if (details instanceof String s && !s.isBlank()) {
            return s;
        }
        return auth.getName();
    }

    /** 冻结请求体 — 前端提交 {reason,duration}。 */
    public record FreezeRequest(String reason, String duration) {}

    /** 解冻请求体 — 前端提交 {reason}。 */
    public record UnfreezeRequest(String reason) {}
}
