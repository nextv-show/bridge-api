package com.sanshuiyuan.admin.application;

import com.sanshuiyuan.admin.api.dto.UserUpsertRequest;
import com.sanshuiyuan.admin.domain.DeviceAsset;
import com.sanshuiyuan.admin.domain.Order;
import com.sanshuiyuan.admin.domain.Sku;
import com.sanshuiyuan.admin.domain.User;
import com.sanshuiyuan.admin.infra.repository.DeviceAssetRepository;
import com.sanshuiyuan.admin.infra.repository.OrderRepository;
import com.sanshuiyuan.admin.infra.repository.SkuRepository;
import com.sanshuiyuan.admin.infra.repository.UserRepository;
import com.sanshuiyuan.admin.infra.client.UserDirectoryClient;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * C 端用户管理服务。
 * users 表为 admin-service 在 asset_db 拥有的去规范化表；订单数/GMV/设备数通过
 * orders / device_assets 实时聚合（均含 user_id）。
 */
@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final List<String> FROZEN_STATUSES = List.of("FROZEN", "BANNED");
    private static final int SYNC_PAGE_SIZE = 200;

    private final UserRepository userRepo;
    private final OrderRepository orderRepo;
    private final DeviceAssetRepository deviceRepo;
    private final SkuRepository skuRepo;
    private final AuditLogService auditLog;
    private final UserDirectoryClient userDirectoryClient;

    public AdminUserService(UserRepository userRepo,
                            OrderRepository orderRepo,
                            DeviceAssetRepository deviceRepo,
                            SkuRepository skuRepo,
                            AuditLogService auditLog,
                            UserDirectoryClient userDirectoryClient) {
        this.userRepo = userRepo;
        this.orderRepo = orderRepo;
        this.deviceRepo = deviceRepo;
        this.skuRepo = skuRepo;
        this.auditLog = auditLog;
        this.userDirectoryClient = userDirectoryClient;
    }

    /* ========== 列表 ========== */

    public Map<String, Object> list(int page, int size, String tab, String channel,
                                    String tier, String q, String sort) {
        Pageable pageable = PageRequest.of(page, size, resolveSort(sort));

        // tab → 过滤条件
        String status = null;
        Collection<String> statusIn = null;
        String kycStatus = null;
        String tagLike = null;
        String tabKey = tab == null ? "ALL" : tab.toUpperCase();
        switch (tabKey) {
            case "KYC_PASS" -> kycStatus = "PASS";
            case "KYC_PEND" -> kycStatus = "PENDING";
            case "RISK" -> tagLike = "%RISK%";
            case "FROZEN" -> statusIn = FROZEN_STATUSES;
            default -> { /* ALL: 无过滤 */ }
        }

        String keyword = (q != null && !q.isBlank()) ? "%" + q.trim() + "%" : null;
        String channelParam = (channel != null && !channel.isBlank()) ? channel : null;
        String tierParam = (tier != null && !tier.isBlank()) ? tier : null;

        Page<User> result = userRepo.search(status, statusIn, channelParam, tierParam,
                kycStatus, tagLike, keyword, pageable);

        List<User> users = result.getContent();
        List<Long> ids = users.stream().map(User::getId).toList();

        Map<Long, long[]> orderAgg = aggregateOrders(ids);   // userId → [count, gmvCents]
        Map<Long, Long> deviceAgg = aggregateDevices(ids);   // userId → count

        List<Map<String, Object>> items = users.stream().map(u -> {
            long[] oa = orderAgg.getOrDefault(u.getId(), new long[]{0L, 0L});
            long dc = deviceAgg.getOrDefault(u.getId(), 0L);
            return toSummary(u, oa[0], oa[1], dc);
        }).toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("total", result.getTotalElements());
        body.put("page", page);
        body.put("size", size);
        return body;
    }

    /**
     * 排序：last_active → lastActiveAt desc；register → createdAt desc；
     * gmv/orders 为聚合字段无法在 DB 排序，回退为 lastActiveAt desc。
     */
    private Sort resolveSort(String sort) {
        String key = sort == null ? "last_active" : sort.toLowerCase();
        return switch (key) {
            case "register" -> Sort.by(Sort.Direction.DESC, "createdAt");
            // gmv / orders 回退到 last_active（聚合字段不可直接排序）
            default -> Sort.by(Sort.Direction.DESC, "lastActiveAt");
        };
    }

    public Map<String, Long> counts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("ALL", userRepo.count());
        counts.put("KYC_PASS", userRepo.countByKycStatus("PASS"));
        counts.put("KYC_PEND", userRepo.countByKycStatus("PENDING"));
        counts.put("RISK", userRepo.countByTagLike("RISK"));
        counts.put("FROZEN", userRepo.countByStatusIn(FROZEN_STATUSES));
        return counts;
    }

    /* ========== 详情 ========== */

    public Map<String, Object> detail(Long id) {
        User u = userRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在: " + id));

        List<Long> ids = List.of(id);
        long[] oa = aggregateOrders(ids).getOrDefault(id, new long[]{0L, 0L});
        long dc = aggregateDevices(ids).getOrDefault(id, 0L);

        Map<String, Object> dto = toSummary(u, oa[0], oa[1], dc);

        // 订单列表
        List<Order> orders = orderRepo.findByUserIdOrderByCreatedAtDesc(id);
        List<Map<String, Object>> orderList = orders.stream().map(o -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.getId());
            m.put("skuId", o.getSkuId());
            m.put("skuName", resolveSkuName(o.getSkuId()));
            m.put("qty", o.getQty());
            m.put("amount", o.getAmountCents());
            m.put("status", o.getStatus() != null ? o.getStatus().name() : "");
            m.put("at", o.getCreatedAt() != null ? o.getCreatedAt().toString() : "");
            return m;
        }).toList();

        // 设备列表
        List<DeviceAsset> devices = deviceRepo.findByUserIdOrderByPurchasedAtDesc(id);
        List<Map<String, Object>> deviceList = devices.stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sn", d.getSn());
            m.put("model", d.getModel());
            m.put("stage", d.getStage() != null ? d.getStage().name() : "");
            m.put("activatedAt", d.getPurchasedAt() != null ? d.getPurchasedAt().toString() : "");
            return m;
        }).toList();

        dto.put("orderList", orderList);
        dto.put("deviceList", deviceList);
        dto.put("addressList", List.of()); // 无地址表，前端占位
        return dto;
    }

    private String resolveSkuName(Long skuId) {
        if (skuId == null) return "SKU #?";
        return skuRepo.findById(skuId).map(Sku::getName).orElse("SKU #" + skuId);
    }

    /* ========== 新建 ========== */

    @Transactional
    public Map<String, Object> create(String role, UserUpsertRequest dto, Long adminId, String adminName) {
        if (!"SUPER_ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅超级管理员可新建用户");
        }
        LocalDateTime now = LocalDateTime.now();
        User u = new User();
        u.setNickname(dto.name());
        u.setRealNameMask(dto.name());
        u.setPhoneMask(dto.phone());
        u.setGender(dto.gender());
        u.setAge(dto.age());
        u.setChannel(dto.channel() != null && !dto.channel().isBlank() ? dto.channel() : "WECHAT_MP");
        u.setTier(dto.tier() != null && !dto.tier().isBlank() ? dto.tier() : "NORMAL");
        u.setTags(joinTags(dto.tags()));
        u.setCity(dto.city());
        u.setNote(dto.note());
        u.setStatus("ACTIVE");
        u.setKycStatus("NONE");
        u.setCreatedAt(now);
        u.setUpdatedAt(now);
        u.setLastActiveAt(now);
        User saved = userRepo.save(u);

        auditLog.log(adminId, "USER_CREATE", "user", String.valueOf(saved.getId()),
                "{\"name\":\"" + escapeJson(dto.name()) + "\"}");
        return toSummary(saved, 0L, 0L, 0L);
    }

    /* ========== 编辑 ========== */

    @Transactional
    public Map<String, Object> update(Long id, UserUpsertRequest dto, Long adminId) {
        User u = userRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在: " + id));
        if (dto.name() != null) {
            u.setNickname(dto.name());
            u.setRealNameMask(dto.name());
        }
        if (dto.phone() != null) u.setPhoneMask(dto.phone());
        if (dto.gender() != null) u.setGender(dto.gender());
        if (dto.age() != null) u.setAge(dto.age());
        if (dto.channel() != null && !dto.channel().isBlank()) u.setChannel(dto.channel());
        if (dto.tier() != null && !dto.tier().isBlank()) u.setTier(dto.tier());
        if (dto.tags() != null) u.setTags(joinTags(dto.tags()));
        if (dto.city() != null) u.setCity(dto.city());
        if (dto.note() != null) u.setNote(dto.note());
        u.setUpdatedAt(LocalDateTime.now());
        userRepo.save(u);

        auditLog.log(adminId, "USER_UPDATE", "user", String.valueOf(id), null);

        List<Long> ids = List.of(id);
        long[] oa = aggregateOrders(ids).getOrDefault(id, new long[]{0L, 0L});
        long dc = aggregateDevices(ids).getOrDefault(id, 0L);
        return toSummary(u, oa[0], oa[1], dc);
    }

    /* ========== 冻结 / 解冻 ========== */

    @Transactional
    public void freeze(Long id, String reason, String duration, Long adminId, String adminName) {
        User u = userRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在: " + id));
        u.setStatus("FROZEN");
        u.setFrozenReason(reason);
        u.setFrozenDuration(duration);
        u.setFrozenAt(LocalDateTime.now());
        u.setFrozenBy(adminName);
        u.setUpdatedAt(LocalDateTime.now());
        userRepo.save(u);

        String payload = "{\"reason\":\"" + escapeJson(reason) + "\",\"duration\":\""
                + escapeJson(duration) + "\"}";
        auditLog.log(adminId, "USER_FREEZE", "user", String.valueOf(id), payload);
    }

    @Transactional
    public void unfreeze(Long id, String reason, Long adminId, String adminName) {
        User u = userRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在: " + id));
        u.setStatus("ACTIVE");
        u.setFrozenReason(null);
        u.setFrozenDuration(null);
        u.setFrozenAt(null);
        u.setFrozenBy(null);
        u.setUpdatedAt(LocalDateTime.now());
        userRepo.save(u);

        String payload = reason != null ? "{\"reason\":\"" + escapeJson(reason) + "\"}" : null;
        auditLog.log(adminId, "USER_UNFREEZE", "user", String.valueOf(id), payload);
    }

    /* ========== 从 user-service 同步 ========== */

    /**
     * 拉取 user-service 真实用户目录并 upsert 到 admin users 表（按 id）。
     * 新用户插入；已存在用户仅更新 nickname/avatarUrl/openid，保留全部 admin 托管字段。
     * 逐用户保存以便部分进度可落库；user-service 不可达时抛 BAD_GATEWAY。
     */
    public Map<String, Object> syncFromUserService(Long adminId, String operator) {
        long scanned = 0;
        long inserted = 0;
        long updated = 0;

        int page = 0;
        while (true) {
            UserDirectoryClient.Page result;
            try {
                result = userDirectoryClient.fetchPage(page, SYNC_PAGE_SIZE);
            } catch (RestClientException e) {
                log.error("USER_SYNC: user-service 不可达 (page={}): {}", page, e.getMessage());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "无法连接 user-service，同步失败: " + e.getMessage());
            }

            List<UserDirectoryClient.SourceUser> items = result.items();
            if (items.isEmpty()) {
                break;
            }

            for (UserDirectoryClient.SourceUser src : items) {
                scanned++;
                Optional<User> existing = src.id() != null ? userRepo.findById(src.id()) : Optional.empty();
                if (existing.isPresent()) {
                    User u = existing.get();
                    u.setNickname(src.nickname());
                    u.setAvatarUrl(src.avatarUrl());
                    u.setOpenid(resolveOpenid(src));
                    u.setUpdatedAt(LocalDateTime.now());
                    userRepo.save(u);
                    updated++;
                } else {
                    User u = new User();
                    u.setId(src.id());
                    u.setOpenid(resolveOpenid(src));
                    u.setNickname(src.nickname());
                    u.setAvatarUrl(src.avatarUrl());
                    u.setChannel(resolveChannel(src));
                    u.setStatus("ACTIVE");
                    u.setKycStatus("NONE");
                    u.setTier("NORMAL");
                    u.setTags("");
                    LocalDateTime createdAt = parseCreatedAt(src.createdAt());
                    u.setCreatedAt(createdAt);
                    u.setLastActiveAt(createdAt);
                    u.setUpdatedAt(LocalDateTime.now());
                    userRepo.save(u);
                    inserted++;
                }
            }

            // 已到末页
            if ((long) (page + 1) * SYNC_PAGE_SIZE >= result.total() || items.size() < SYNC_PAGE_SIZE) {
                break;
            }
            page++;
        }

        String payload = "{\"scanned\":" + scanned + ",\"inserted\":" + inserted
                + ",\"updated\":" + updated + "}";
        auditLog.log(adminId, "USER_SYNC", "user", "ALL", payload);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scanned", scanned);
        body.put("inserted", inserted);
        body.put("updated", updated);
        return body;
    }

    private String resolveOpenid(UserDirectoryClient.SourceUser src) {
        return src.openidWx() != null ? src.openidWx() : src.openidApp();
    }

    private String resolveChannel(UserDirectoryClient.SourceUser src) {
        if (src.openidWx() != null) return "WECHAT_MP";
        if (src.openidApp() != null) return "IOS";
        return "WECHAT_MP";
    }

    private LocalDateTime parseCreatedAt(String iso) {
        if (iso == null || iso.isBlank()) return LocalDateTime.now();
        try {
            return LocalDateTime.parse(iso);
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    /* ========== 聚合辅助 ========== */

    private Map<Long, long[]> aggregateOrders(Collection<Long> ids) {
        Map<Long, long[]> map = new HashMap<>();
        if (ids == null || ids.isEmpty()) return map;
        for (Object[] row : orderRepo.aggregateByUserIds(ids)) {
            Long userId = ((Number) row[0]).longValue();
            long count = ((Number) row[1]).longValue();
            long gmv = ((Number) row[2]).longValue();
            map.put(userId, new long[]{count, gmv});
        }
        return map;
    }

    private Map<Long, Long> aggregateDevices(Collection<Long> ids) {
        Map<Long, Long> map = new HashMap<>();
        if (ids == null || ids.isEmpty()) return map;
        for (Object[] row : deviceRepo.countByUserIds(ids)) {
            Long userId = ((Number) row[0]).longValue();
            long count = ((Number) row[1]).longValue();
            map.put(userId, count);
        }
        return map;
    }

    /* ========== 共享摘要映射（前端依赖的字段名） ========== */

    private Map<String, Object> toSummary(User u, long orderCount, long gmvCents, long deviceCount) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("name", u.getRealNameMask() != null ? u.getRealNameMask() : u.getNickname());
        m.put("phone", u.getPhoneMask());
        m.put("gender", u.getGender());
        m.put("age", u.getAge());
        m.put("openid", u.getOpenid());
        m.put("channel", u.getChannel());
        m.put("status", u.getStatus());
        m.put("kyc", u.getKycStatus());
        m.put("tier", u.getTier());
        m.put("tags", u.tagList());
        m.put("orders", (int) orderCount);
        m.put("gmv", gmvCents);
        m.put("devices", (int) deviceCount);
        m.put("addresses", 0);
        m.put("registeredAt", u.getCreatedAt() != null ? u.getCreatedAt().format(DATE_FMT) : "");
        m.put("lastActiveAt", u.getLastActiveAt() != null ? u.getLastActiveAt().format(DATETIME_FMT) : "");
        m.put("city", u.getCity());
        m.put("kycVerifiedAt", u.getKycVerifiedAt() != null ? u.getKycVerifiedAt().format(DATETIME_FMT) : null);
        m.put("frozenReason", u.getFrozenReason());
        return m;
    }

    private String joinTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) return "";
        return String.join(",", tags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
