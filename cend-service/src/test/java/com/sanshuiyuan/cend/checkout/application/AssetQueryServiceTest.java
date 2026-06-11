package com.sanshuiyuan.cend.checkout.application;

import com.sanshuiyuan.cend.checkout.api.dto.AssetDto;
import com.sanshuiyuan.cend.checkout.domain.DeviceSpec;
import com.sanshuiyuan.cend.checkout.domain.CendOrder;
import com.sanshuiyuan.cend.checkout.domain.Invoice;
import com.sanshuiyuan.cend.checkout.domain.InvoiceStatus;
import com.sanshuiyuan.cend.checkout.domain.OrderStatus;
import com.sanshuiyuan.cend.checkout.infra.repository.DeviceSpecRepository;
import com.sanshuiyuan.cend.checkout.infra.repository.CendOrderRepository;
import com.sanshuiyuan.cend.checkout.infra.repository.InvoiceRepository;
import com.sanshuiyuan.cend.common.BizException;
import com.sanshuiyuan.cend.common.ErrorCode;
import com.sanshuiyuan.cend.identity.IdentityResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetQueryServiceTest {

    @Mock CendOrderRepository orderRepo;
    @Mock DeviceSpecRepository specRepo;
    @Mock InvoiceRepository invoiceRepo;
    @Mock IdentityResolver identityResolver;

    @BeforeEach
    void stubOwnership() {
        // 默认归属语义 = 旧的 openid 严格相等（未实名/同端）；跨端聚合另由 IdentityResolverTest 覆盖。
        lenient().when(identityResolver.owns(anyString(), anyString()))
                .thenAnswer(inv -> inv.getArgument(0).equals(inv.getArgument(1)));
    }

    private AssetQueryService createService() {
        return new AssetQueryService(orderRepo, specRepo, invoiceRepo, identityResolver);
    }

    // ─── helpers ───

    private CendOrder buildOrder(Long id, String openid, String specId, String modelCode,
                               long amountCents, OrderStatus status, String sn,
                               LocalDateTime cooldownEndAt) {
        CendOrder order = CendOrder.create("ORD" + id, openid, specId, modelCode,
                amountCents, "WX_JSAPI");
        setField(order, "id", id);
        if (status == OrderStatus.PAID || status == OrderStatus.REFUNDING || status == OrderStatus.REFUNDED) {
            order.markPaid("wx-txn-123", sn, cooldownEndAt);
        }
        // Override status if needed
        if (status == OrderStatus.REFUNDING) {
            order.markRefunding();
        } else if (status == OrderStatus.REFUNDED) {
            order.markRefunded();
        }
        return order;
    }

    private DeviceSpec buildSpec(String specId, String name) {
        try {
            var ctor = DeviceSpec.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            DeviceSpec s = ctor.newInstance();
            setField(s, "specId", specId);
            setField(s, "name", name);
            return s;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Invoice buildInvoice(Long orderId, InvoiceStatus status, String downloadUrl) {
        Invoice inv = Invoice.createForOrder(orderId);
        if (status == InvoiceStatus.ISSUED) {
            inv.markIssued(downloadUrl);
        }
        return inv;
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

    // ─── 场景 1: 本人查询返回资产信息 ───

    @Test
    void queryAsset_ownerQuery_returnsFullAsset() {
        LocalDateTime cooldownEnd = LocalDateTime.now().plusHours(18);
        CendOrder order = buildOrder(1L, "user-A", "home-pro", "BR-H2",
                680000L, OrderStatus.PAID, "SN-001", cooldownEnd);
        DeviceSpec spec = buildSpec("home-pro", "全屋净水 Pro");
        Invoice invoice = buildInvoice(1L, InvoiceStatus.ISSUED, "https://cdn.example.com/inv.pdf");

        when(orderRepo.findById(1L)).thenReturn(Optional.of(order));
        when(specRepo.findBySpecId("home-pro")).thenReturn(Optional.of(spec));
        when(invoiceRepo.findByOrderId(1L)).thenReturn(Optional.of(invoice));

        AssetDto result = createService().queryAsset(1L, "user-A");

        assertThat(result.orderNo()).isEqualTo("ORD1");
        assertThat(result.modelName()).isEqualTo("全屋净水 Pro");
        assertThat(result.paidAmountCents()).isEqualTo(680000L);
        assertThat(result.cooldownEndAt()).isNotNull();
        assertThat(result.sn()).isEqualTo("SN-001");
        assertThat(result.orderStatus()).isEqualTo("PAID");
        assertThat(result.invoiceStatus()).isEqualTo("issued");
        assertThat(result.cooldownRemainingSeconds()).isGreaterThan(0);
    }

    // ─── 场景 2: 非本人 403 ───

    @Test
    void queryAsset_notOwner_throws403() {
        CendOrder order = buildOrder(2L, "user-B", "spec-1", "M1",
                10000L, OrderStatus.PAID, "SN-002", LocalDateTime.now().plusHours(5));

        when(orderRepo.findById(2L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> createService().queryAsset(2L, "attacker"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).errorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    // ─── 场景 3: 不存在 404 ───

    @Test
    void queryAsset_orderNotFound_throws404() {
        when(orderRepo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> createService().queryAsset(999L, "anyone"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).errorCode())
                        .isEqualTo(ErrorCode.ORDER_NOT_FOUND));
    }

    // ─── 场景 4: 已退款订单仍可查 ───

    @Test
    void queryAsset_refundedOrder_stillQueryable() {
        CendOrder order = buildOrder(3L, "user-C", "spec-2", "M2",
                29900L, OrderStatus.REFUNDED, "SN-003", LocalDateTime.now().minusHours(2));

        when(orderRepo.findById(3L)).thenReturn(Optional.of(order));
        when(specRepo.findBySpecId("spec-2")).thenReturn(Optional.of(buildSpec("spec-2", "净水器")));
        when(invoiceRepo.findByOrderId(3L)).thenReturn(Optional.empty());

        AssetDto result = createService().queryAsset(3L, "user-C");

        assertThat(result.orderStatus()).isEqualTo("REFUNDED");
        assertThat(result.paidAmountCents()).isEqualTo(29900L);
        // cooldown remaining should be 0 for refunded order (not PAID)
        assertThat(result.cooldownRemainingSeconds()).isEqualTo(0);
    }

    // ─── 场景 5a: cooldownEndAt 优先使用列值 ───

    @Test
    void queryAsset_cooldownEndAt_usesColumnValue() {
        LocalDateTime cooldownEnd = LocalDateTime.now().plusHours(12);
        CendOrder order = buildOrder(4L, "user-D", "spec-3", "M3",
                50000L, OrderStatus.PAID, "SN-004", cooldownEnd);

        when(orderRepo.findById(4L)).thenReturn(Optional.of(order));
        when(specRepo.findBySpecId("spec-3")).thenReturn(Optional.of(buildSpec("spec-3", "机型3")));
        when(invoiceRepo.findByOrderId(4L)).thenReturn(Optional.empty());

        AssetDto result = createService().queryAsset(4L, "user-D");

        // cooldownEndAt should be populated from the column value
        assertThat(result.cooldownEndAt()).isNotNull();
        assertThat(result.cooldownRemainingSeconds()).isGreaterThan(0);
    }

    // ─── 场景 5b: cooldownEndAt 为 null 时的兜底表现 ───

    @Test
    void queryAsset_noCooldownEndAt_remainingSecondsZero() {
        CendOrder order = buildOrder(5L, "user-E", "spec-4", "M4",
                15000L, OrderStatus.PAID, "SN-005", null);

        when(orderRepo.findById(5L)).thenReturn(Optional.of(order));
        when(specRepo.findBySpecId("spec-4")).thenReturn(Optional.of(buildSpec("spec-4", "机型4")));
        when(invoiceRepo.findByOrderId(5L)).thenReturn(Optional.empty());

        AssetDto result = createService().queryAsset(5L, "user-E");

        // When cooldownEndAt is null, cooldownEndAt string should be null, seconds = 0
        assertThat(result.cooldownEndAt()).isNull();
        assertThat(result.cooldownRemainingSeconds()).isEqualTo(0);
    }

    // ─── 额外: specRepo 未找到时用 modelCode 兜底 ───

    @Test
    void queryAsset_specNotFound_fallsBackToModelCode() {
        CendOrder order = buildOrder(6L, "user-F", "spec-missing", "FALLBACK-MODEL",
                10000L, OrderStatus.PAID, "SN-006", LocalDateTime.now().plusHours(5));

        when(orderRepo.findById(6L)).thenReturn(Optional.of(order));
        when(specRepo.findBySpecId("spec-missing")).thenReturn(Optional.empty());
        when(invoiceRepo.findByOrderId(6L)).thenReturn(Optional.empty());

        AssetDto result = createService().queryAsset(6L, "user-F");

        assertThat(result.modelName()).isEqualTo("FALLBACK-MODEL");
    }

    // ─── 额外: 无发票记录时返回 issuing ───

    @Test
    void queryAsset_noInvoice_returnsIssuing() {
        CendOrder order = buildOrder(7L, "user-G", "spec-5", "M5",
                20000L, OrderStatus.PAID, "SN-007", LocalDateTime.now().plusHours(5));

        when(orderRepo.findById(7L)).thenReturn(Optional.of(order));
        when(specRepo.findBySpecId("spec-5")).thenReturn(Optional.of(buildSpec("spec-5", "机型5")));
        when(invoiceRepo.findByOrderId(7L)).thenReturn(Optional.empty());

        AssetDto result = createService().queryAsset(7L, "user-G");

        assertThat(result.invoiceStatus()).isEqualTo("issuing");
    }
}
