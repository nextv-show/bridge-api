package com.sanshuiyuan.cend.referral;

import com.sanshuiyuan.cend.checkout.domain.CendOrder;
import com.sanshuiyuan.cend.checkout.domain.KycRecord;
import com.sanshuiyuan.cend.checkout.domain.KycStatus;
import com.sanshuiyuan.cend.checkout.domain.OrderStatus;
import com.sanshuiyuan.cend.checkout.infra.repository.CendOrderRepository;
import com.sanshuiyuan.cend.checkout.infra.repository.KycRecordRepository;
import com.sanshuiyuan.cend.common.BizException;
import com.sanshuiyuan.cend.referral.api.MyReferralsResponse;
import com.sanshuiyuan.cend.referral.api.ReferralItemResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T15.2：我的推荐查询服务单测。
 * <ul>
 *   <li>仅 L1 单层展开（findByInviterId），按被推荐人 openid 的 PAID 订单判定是否已购买；</li>
 *   <li>status 过滤（ALL/REGISTERED/PURCHASED，非法值按 ALL）；汇总计数正确；</li>
 *   <li>DTO 零层级字段；昵称脱敏；最近购买取最大 paidAt。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ReferralQueryServiceTest {

    @Mock CendUserRepository userRepo;
    @Mock CendOrderRepository orderRepo;
    @Mock KycRecordRepository kycRepo;

    private ReferralQueryService service;

    @BeforeEach
    void setUp() {
        // 用真实展示名解析器 + mock KYC 仓库，既验证昵称脱敏不回归，又能覆盖实名/手机尾号兜底。
        service = new ReferralQueryService(userRepo, orderRepo, new ReferralDisplayNameResolver(kycRepo));
        // 默认无 KYC 记录（有昵称的用例走昵称脱敏，不读 KYC）；个别用例覆盖此桩。
        lenient().when(kycRepo.findAllByOpenidInAndStatus(anyCollection(), eq(KycStatus.PASS)))
                .thenReturn(List.of());
    }

    private static KycRecord passKyc(String openid, String realNameMask, String phoneMask) {
        return KycRecord.create(openid, new byte[]{1}, new byte[]{1},
                realNameMask, "110***********12", "hash-" + openid,
                "cert-" + openid, "alipay", new byte[]{1}, phoneMask);
    }

    private static CendUser user(long id, String openid, String nickname, LocalDateTime createdAt) {
        CendUser u = CendUser.create(openid);
        u.updateProfile(nickname, "https://wx.qq.com/" + id + ".png");
        setField(u, "id", id);
        setField(u, "createdAt", createdAt);
        return u;
    }

    private static CendOrder paidOrder(String openid, LocalDateTime paidAt) {
        CendOrder o = CendOrder.create("NO-" + openid + "-" + paidAt.getNano(),
                openid, "spec", "model", 100L, "wxpay");
        setField(o, "status", OrderStatus.PAID);
        setField(o, "paidAt", paidAt);
        return o;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void myReferrals_all_summaryAndItems_withMasking() {
        CendUser me = user(1L, "me", "我", LocalDateTime.of(2026, 1, 1, 0, 0));
        CendUser a = user(10L, "alice", "爱丽丝", LocalDateTime.of(2026, 5, 1, 0, 0)); // 已购买
        CendUser b = user(11L, "bob", "小明", LocalDateTime.of(2026, 5, 2, 0, 0));   // 未购买
        when(userRepo.findByOpenid("me")).thenReturn(Optional.of(me));
        when(userRepo.findByInviterId(1L)).thenReturn(List.of(a, b));
        when(orderRepo.findByOpenidInAndStatus(anyCollection(), eq(OrderStatus.PAID)))
                .thenReturn(List.of(
                        paidOrder("alice", LocalDateTime.of(2026, 5, 10, 9, 0)),
                        paidOrder("alice", LocalDateTime.of(2026, 5, 20, 9, 0)))); // 取最近一笔

        MyReferralsResponse resp = service.myReferrals("me", "ALL");

        assertThat(resp.summary().totalCount()).isEqualTo(2);
        assertThat(resp.summary().purchasedCount()).isEqualTo(1);
        assertThat(resp.summary().registeredCount()).isEqualTo(1);
        assertThat(resp.items()).hasSize(2);

        // 倒序：bob(05-02) 在前，alice(05-01) 在后。
        ReferralItemResponse first = resp.items().get(0);
        assertThat(first.userId()).isEqualTo(11L);
        assertThat(first.nicknameMasked()).isEqualTo("小*"); // 2 字昵称仅「首字 + *」
        assertThat(first.displayName()).isEqualTo("小*");    // 有昵称：展示名 = 昵称脱敏
        assertThat(first.status()).isEqualTo("REGISTERED");
        assertThat(first.purchasedAt()).isNull();

        ReferralItemResponse second = resp.items().get(1);
        assertThat(second.userId()).isEqualTo(10L);
        assertThat(second.status()).isEqualTo("PURCHASED");
        assertThat(second.registeredAt()).isEqualTo("2026-05-01");
        assertThat(second.purchasedAt()).isEqualTo("2026-05-20"); // 最近购买
    }

    @Test
    void myReferrals_nullNickname_fallsBackToKycMaskThenWeChatUser() {
        CendUser me = user(1L, "me", "我", LocalDateTime.of(2026, 1, 1, 0, 0));
        // 无微信昵称（最常见）：alice 有 PASS 实名 → 展示实名脱敏；bob 无 KYC → 空串（前端兜底「微信用户」）。
        CendUser a = user(10L, "alice", null, LocalDateTime.of(2026, 5, 1, 0, 0));
        CendUser b = user(11L, "bob", null, LocalDateTime.of(2026, 5, 2, 0, 0));
        when(userRepo.findByOpenid("me")).thenReturn(Optional.of(me));
        when(userRepo.findByInviterId(1L)).thenReturn(List.of(a, b));
        when(orderRepo.findByOpenidInAndStatus(anyCollection(), eq(OrderStatus.PAID)))
                .thenReturn(List.of());
        when(kycRepo.findAllByOpenidInAndStatus(anyCollection(), eq(KycStatus.PASS)))
                .thenReturn(List.of(passKyc("alice", "张 **", "138****8888")));

        MyReferralsResponse resp = service.myReferrals("me", "ALL");

        // 倒序：bob(05-02) 在前。
        ReferralItemResponse bob = resp.items().get(0);
        assertThat(bob.userId()).isEqualTo(11L);
        assertThat(bob.nicknameMasked()).isEmpty();
        assertThat(bob.displayName()).isEmpty(); // 无昵称无 KYC → 空串，前端显示「微信用户」

        ReferralItemResponse alice = resp.items().get(1);
        assertThat(alice.userId()).isEqualTo(10L);
        assertThat(alice.nicknameMasked()).isEmpty();
        assertThat(alice.displayName()).isEqualTo("张 **"); // 无昵称 → 回落到 KYC 实名脱敏
    }

    @Test
    void myReferrals_nullNickname_noRealNameMask_fallsBackToPhoneTail() {
        CendUser me = user(1L, "me", "我", LocalDateTime.of(2026, 1, 1, 0, 0));
        CendUser a = user(10L, "alice", null, LocalDateTime.of(2026, 5, 1, 0, 0));
        when(userRepo.findByOpenid("me")).thenReturn(Optional.of(me));
        when(userRepo.findByInviterId(1L)).thenReturn(List.of(a));
        when(orderRepo.findByOpenidInAndStatus(anyCollection(), eq(OrderStatus.PAID)))
                .thenReturn(List.of());
        when(kycRepo.findAllByOpenidInAndStatus(anyCollection(), eq(KycStatus.PASS)))
                .thenReturn(List.of(passKyc("alice", "", "138****8888")));

        MyReferralsResponse resp = service.myReferrals("me", "ALL");

        assertThat(resp.items().get(0).displayName()).isEqualTo("138****8888"); // 实名脱敏缺失 → 手机尾号
    }

    @Test
    void myReferrals_registeredFilter_onlyUnpurchased() {
        CendUser me = user(1L, "me", "我", LocalDateTime.of(2026, 1, 1, 0, 0));
        CendUser a = user(10L, "alice", "爱丽丝", LocalDateTime.of(2026, 5, 1, 0, 0));
        CendUser b = user(11L, "bob", "小明", LocalDateTime.of(2026, 5, 2, 0, 0));
        when(userRepo.findByOpenid("me")).thenReturn(Optional.of(me));
        when(userRepo.findByInviterId(1L)).thenReturn(List.of(a, b));
        when(orderRepo.findByOpenidInAndStatus(anyCollection(), eq(OrderStatus.PAID)))
                .thenReturn(List.of(paidOrder("alice", LocalDateTime.of(2026, 5, 10, 9, 0))));

        MyReferralsResponse resp = service.myReferrals("me", "REGISTERED");

        // 汇总反映全量；列表仅含未购买。
        assertThat(resp.summary().totalCount()).isEqualTo(2);
        assertThat(resp.summary().purchasedCount()).isEqualTo(1);
        assertThat(resp.items()).hasSize(1);
        assertThat(resp.items().get(0).userId()).isEqualTo(11L);
    }

    @Test
    void myReferrals_purchasedFilter_onlyPurchased() {
        CendUser me = user(1L, "me", "我", LocalDateTime.of(2026, 1, 1, 0, 0));
        CendUser a = user(10L, "alice", "爱丽丝", LocalDateTime.of(2026, 5, 1, 0, 0));
        CendUser b = user(11L, "bob", "小明", LocalDateTime.of(2026, 5, 2, 0, 0));
        when(userRepo.findByOpenid("me")).thenReturn(Optional.of(me));
        when(userRepo.findByInviterId(1L)).thenReturn(List.of(a, b));
        when(orderRepo.findByOpenidInAndStatus(anyCollection(), eq(OrderStatus.PAID)))
                .thenReturn(List.of(paidOrder("alice", LocalDateTime.of(2026, 5, 10, 9, 0))));

        MyReferralsResponse resp = service.myReferrals("me", "PURCHASED");

        assertThat(resp.items()).hasSize(1);
        assertThat(resp.items().get(0).userId()).isEqualTo(10L);
        assertThat(resp.items().get(0).status()).isEqualTo("PURCHASED");
    }

    @Test
    void myReferrals_invalidStatus_treatedAsAll() {
        CendUser me = user(1L, "me", "我", LocalDateTime.of(2026, 1, 1, 0, 0));
        CendUser a = user(10L, "alice", "爱丽丝", LocalDateTime.of(2026, 5, 1, 0, 0));
        when(userRepo.findByOpenid("me")).thenReturn(Optional.of(me));
        when(userRepo.findByInviterId(1L)).thenReturn(List.of(a));
        when(orderRepo.findByOpenidInAndStatus(anyCollection(), eq(OrderStatus.PAID)))
                .thenReturn(List.of());

        MyReferralsResponse resp = service.myReferrals("me", "HACK; DROP TABLE");

        assertThat(resp.items()).hasSize(1); // 当 ALL 处理
    }

    @Test
    void myReferrals_noReferrals_emptyAndNoOrderQuery() {
        CendUser me = user(1L, "me", "我", LocalDateTime.of(2026, 1, 1, 0, 0));
        when(userRepo.findByOpenid("me")).thenReturn(Optional.of(me));
        when(userRepo.findByInviterId(1L)).thenReturn(List.of());

        MyReferralsResponse resp = service.myReferrals("me", "ALL");

        assertThat(resp.summary().totalCount()).isZero();
        assertThat(resp.items()).isEmpty();
        verify(orderRepo, never()).findByOpenidInAndStatus(anyCollection(), any());
    }

    @Test
    void myReferrals_unknownOpenid_throwsUnauthorized() {
        when(userRepo.findByOpenid("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.myReferrals("ghost", "ALL"))
                .isInstanceOf(BizException.class);
        verify(userRepo, never()).findByInviterId(any());
    }
}
