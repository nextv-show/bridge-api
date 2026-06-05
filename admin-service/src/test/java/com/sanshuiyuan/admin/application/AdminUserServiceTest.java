package com.sanshuiyuan.admin.application;

import com.sanshuiyuan.admin.api.dto.UserUpsertRequest;
import com.sanshuiyuan.admin.domain.KycRecord;
import com.sanshuiyuan.admin.domain.User;
import com.sanshuiyuan.admin.infra.client.UserDirectoryClient;
import com.sanshuiyuan.admin.infra.repository.DeviceAssetRepository;
import com.sanshuiyuan.admin.infra.repository.KycRecordRepository;
import com.sanshuiyuan.admin.infra.repository.OrderRepository;
import com.sanshuiyuan.admin.infra.repository.SkuRepository;
import com.sanshuiyuan.admin.infra.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock private UserRepository userRepo;
    @Mock private OrderRepository orderRepo;
    @Mock private DeviceAssetRepository deviceRepo;
    @Mock private SkuRepository skuRepo;
    @Mock private KycRecordRepository kycRepo;
    @Mock private AuditLogService auditLog;
    @Mock private UserDirectoryClient userDirectoryClient;
    @Mock private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private AdminUserService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserService(userRepo, orderRepo, deviceRepo, skuRepo, kycRepo, auditLog,
                userDirectoryClient, jdbcTemplate);
    }

    /** 构造一条 KYC 记录（反射写私有字段），供实名派生测试。 */
    private KycRecord kycRecord(String openid, KycRecord.Status status, LocalDateTime verifiedAt) {
        KycRecord k = new KycRecord();
        setField(k, "openid", openid);
        setField(k, "status", status);
        setField(k, "verifiedAt", verifiedAt);
        return k;
    }

    private User user(Long id, String status, String kyc) {
        User u = new User();
        setField(u, "id", id);
        setField(u, "nickname", "王*杰");
        setField(u, "realNameMask", "王*杰");
        setField(u, "phoneMask", "138****5821");
        setField(u, "gender", "MALE");
        setField(u, "age", 34);
        setField(u, "openid", "oWxMp_test");
        setField(u, "channel", "WECHAT_MP");
        setField(u, "tier", "VIP");
        setField(u, "tags", "REPEAT,HIGH_GMV");
        setField(u, "city", "上海 · 浦东");
        setField(u, "status", status);
        setField(u, "kycStatus", kyc);
        setField(u, "createdAt", LocalDateTime.of(2024, 3, 11, 9, 0));
        setField(u, "lastActiveAt", LocalDateTime.of(2026, 5, 24, 21, 13));
        setField(u, "updatedAt", LocalDateTime.now());
        return u;
    }

    private void setField(Object target, String name, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /* ========== list / 映射 ========== */

    @Test
    void list_mapsSummaryFieldsAndAggregations() {
        User u = user(14821L, "ACTIVE", "PASS");
        Page<User> page = new PageImpl<>(List.of(u), Pageable.ofSize(20), 1);
        when(userRepo.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(page);
        when(orderRepo.aggregateByUserIds(anyList()))
                .thenReturn(List.<Object[]>of(new Object[]{14821L, 2L, 899700L}));
        when(deviceRepo.countByUserIds(anyList()))
                .thenReturn(List.<Object[]>of(new Object[]{14821L, 2L}));
        // 实名按 kyc_records 实时派生：该用户 openid 有一条 PASS 记录。
        when(kycRepo.findByOpenidIn(anyList()))
                .thenReturn(List.of(kycRecord("oWxMp_test", KycRecord.Status.PASS,
                        LocalDateTime.of(2026, 5, 26, 16, 43))));

        Map<String, Object> body = service.list(0, 20, "ALL", null, null, null, "last_active");

        assertEquals(1L, body.get("total"));
        assertEquals(0, body.get("page"));
        assertEquals(20, body.get("size"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        assertEquals(1, items.size());
        Map<String, Object> item = items.get(0);

        assertEquals(14821L, item.get("id"));
        assertEquals("王*杰", item.get("name"));
        assertEquals("138****5821", item.get("phone"));
        assertEquals("MALE", item.get("gender"));
        assertEquals(34, item.get("age"));
        assertEquals("WECHAT_MP", item.get("channel"));
        assertEquals("ACTIVE", item.get("status"));
        assertEquals("PASS", item.get("kyc"));
        assertEquals("2026-05-26 16:43", item.get("kycVerifiedAt"));
        assertEquals("VIP", item.get("tier"));
        assertEquals(List.of("REPEAT", "HIGH_GMV"), item.get("tags"));
        assertEquals(2, item.get("orders"));
        assertEquals(899700L, item.get("gmv"));
        assertEquals(2, item.get("devices"));
        assertEquals(0, item.get("addresses"));
        assertEquals("2024-03-11", item.get("registeredAt"));
        assertEquals("2026-05-24 21:13", item.get("lastActiveAt"));
        assertEquals("上海 · 浦东", item.get("city"));
    }

    @Test
    void list_defaultsZeroWhenNoAggregation() {
        User u = user(14808L, "ACTIVE", "NONE");
        when(userRepo.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(u), Pageable.ofSize(20), 1));
        when(orderRepo.aggregateByUserIds(anyList())).thenReturn(List.of());
        when(deviceRepo.countByUserIds(anyList())).thenReturn(List.of());

        Map<String, Object> body = service.list(0, 20, "ALL", null, null, null, "last_active");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        assertEquals(0, items.get(0).get("orders"));
        assertEquals(0L, items.get(0).get("gmv"));
        assertEquals(0, items.get(0).get("devices"));
    }

    /* ========== counts ========== */

    @Test
    void counts_returnsAllTabKeys() {
        when(userRepo.count()).thenReturn(14L);
        when(userRepo.countKycPass(KycRecord.Status.PASS)).thenReturn(6L);
        when(userRepo.countKycPending(KycRecord.Status.PENDING, KycRecord.Status.PASS)).thenReturn(3L);
        when(userRepo.countByTagLike("RISK")).thenReturn(2L);
        when(userRepo.countByStatusIn(List.of("FROZEN", "BANNED"))).thenReturn(2L);

        Map<String, Long> counts = service.counts();
        assertEquals(14L, counts.get("ALL"));
        assertEquals(6L, counts.get("KYC_PASS"));
        assertEquals(3L, counts.get("KYC_PEND"));
        assertEquals(2L, counts.get("RISK"));
        assertEquals(2L, counts.get("FROZEN"));
    }

    /* ========== create ========== */

    @Test
    void create_nonSuperAdmin_forbidden() {
        UserUpsertRequest req = new UserUpsertRequest("138****0000", "测*试", "MALE", 30,
                "WECHAT_MP", "NORMAL", List.of("REPEAT"), "上海", null);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.create("OPS", req, 1L, "ops1"));
        assertEquals(403, ex.getStatusCode().value());
        verify(userRepo, never()).save(any());
    }

    @Test
    void create_superAdmin_savesAndAudits() {
        UserUpsertRequest req = new UserUpsertRequest("138****0000", "测*试", "FEMALE", 28,
                "IOS", "GOLD", List.of("REFERRER"), "北京", "备注");
        when(userRepo.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            setField(u, "id", 99L);
            return u;
        });

        Map<String, Object> summary = service.create("SUPER_ADMIN", req, 1L, "super");

        assertEquals(99L, summary.get("id"));
        assertEquals("测*试", summary.get("name"));
        assertEquals("GOLD", summary.get("tier"));
        assertEquals(List.of("REFERRER"), summary.get("tags"));
        assertEquals(0, summary.get("orders"));
        verify(auditLog).log(eq(1L), eq("USER_CREATE"), eq("user"), eq("99"), anyString());
    }

    /* ========== detail ========== */

    @Test
    void detail_notFound_throws404() {
        when(userRepo.findById(404L)).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.detail(404L));
        assertEquals(404, ex.getStatusCode().value());
    }

    /* ========== freeze / unfreeze ========== */

    @Test
    void freeze_setsStatusAndAudits() {
        User u = user(14813L, "ACTIVE", "REJECT");
        when(userRepo.findById(14813L)).thenReturn(Optional.of(u));

        service.freeze(14813L, "风控人工冻结", "7d", 1L, "risk_ops");

        assertEquals("FROZEN", u.getStatus());
        assertEquals("风控人工冻结", u.getFrozenReason());
        assertEquals("7d", u.getFrozenDuration());
        assertEquals("risk_ops", u.getFrozenBy());
        assertNotNull(u.getFrozenAt());
        verify(userRepo).save(u);
        verify(auditLog).log(eq(1L), eq("USER_FREEZE"), eq("user"), eq("14813"), anyString());
    }

    @Test
    void unfreeze_clearsFrozenFields() {
        User u = user(14813L, "FROZEN", "REJECT");
        setField(u, "frozenReason", "x");
        setField(u, "frozenAt", LocalDateTime.now());
        when(userRepo.findById(14813L)).thenReturn(Optional.of(u));

        service.unfreeze(14813L, "复核通过", 1L, "risk_ops");

        assertEquals("ACTIVE", u.getStatus());
        assertNull(u.getFrozenReason());
        assertNull(u.getFrozenAt());
        verify(auditLog).log(eq(1L), eq("USER_UNFREEZE"), eq("user"), eq("14813"), anyString());
    }

    /* ========== syncFromUserService ========== */

    @Test
    void sync_insertsNewAndPreservesAdminFieldsOnExisting() {
        // 源：一个新用户（微信小程序），一个已存在用户（其 admin 字段不可被覆盖）
        UserDirectoryClient.SourceUser newSrc = new UserDirectoryClient.SourceUser(
                501L, "u-501", "oWxMp_501", null, "新用户", "https://avatar/501.png",
                "CONSUMER", "2026-01-02T10:30:00");
        UserDirectoryClient.SourceUser existSrc = new UserDirectoryClient.SourceUser(
                14821L, "u-14821", "oWxMp_new", null, "更新后的昵称", "https://avatar/new.png",
                "OWNER", "2026-02-02T08:00:00");

        when(userDirectoryClient.fetchPage(0, 200)).thenReturn(
                new UserDirectoryClient.Page(List.of(newSrc, existSrc), 2L, 0, 200));

        // 14821 已存在，且带非默认 status/tier/tags 等 admin 托管字段
        User existing = user(14821L, "FROZEN", "PASS");
        setField(existing, "tier", "VIP");
        setField(existing, "tags", "REPEAT,HIGH_GMV");
        setField(existing, "phoneMask", "138****5821");
        LocalDateTime origCreated = LocalDateTime.of(2024, 3, 11, 9, 0);
        setField(existing, "createdAt", origCreated);

        when(userRepo.findById(501L)).thenReturn(Optional.empty());
        when(userRepo.findById(14821L)).thenReturn(Optional.of(existing));
        when(userRepo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = service.syncFromUserService(7L, "admin");

        assertEquals(2L, result.get("scanned"));
        assertEquals(1L, result.get("inserted"));
        assertEquals(1L, result.get("updated"));

        // 新用户走原生 INSERT，显式保留来源 id 501（位置参数逐一匹配）；
        // 仅 update 路径用 userRepo.save
        verify(userRepo, times(1)).save(any(User.class));
        LocalDateTime srcCreated = LocalDateTime.of(2026, 1, 2, 10, 30);
        verify(jdbcTemplate).update(
                contains("INSERT INTO users"),
                eq(501L), eq("oWxMp_501"), eq("新用户"), eq("https://avatar/501.png"),
                eq("WECHAT_MP"), eq("NORMAL"), eq(""), eq("ACTIVE"), eq("NONE"),
                eq(srcCreated), eq(srcCreated));

        // 已存在用户：admin 托管字段保留，仅 nickname/avatar/openid 更新
        assertEquals("FROZEN", existing.getStatus());
        assertEquals("VIP", existing.getTier());
        assertEquals("REPEAT,HIGH_GMV", existing.getTags());
        assertEquals("138****5821", existing.getPhoneMask());
        assertEquals("PASS", existing.getKycStatus());
        assertEquals(origCreated, existing.getCreatedAt());
        assertEquals("更新后的昵称", existing.getNickname());
        assertEquals("https://avatar/new.png", existing.getAvatarUrl());
        assertEquals("oWxMp_new", existing.getOpenid());

        verify(auditLog).log(eq(7L), eq("USER_SYNC"), eq("user"), eq("ALL"), anyString());
    }

    @Test
    void sync_userServiceUnreachable_throwsBadGateway() {
        when(userDirectoryClient.fetchPage(0, 200))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("connection refused"));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.syncFromUserService(7L, "admin"));
        assertEquals(502, ex.getStatusCode().value());
        verify(auditLog, never()).log(any(), any(), any(), any(), any());
    }
}
