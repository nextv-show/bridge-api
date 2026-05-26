package com.sanshuiyuan.h5.referral;

import com.sanshuiyuan.h5.common.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T14.1：H5 关系链绑定服务单测。
 * <ul>
 *   <li>{@code onWxLogin} 仅定位/创建本人，<b>绝不绑定</b>关系链；</li>
 *   <li>{@code confirmBinding} 仅在用户显式确认后绑定 L1/L2：确认才绑定、已绑定幂等、解码失败/自我邀请降级。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ReferralBindingServiceTest {

    private static final String SECRET = "unit-test-referral-secret-0123456789";

    @Mock H5UserRepository userRepo;

    private RefIdCodec codec;
    private ReferralBindingService service;

    @BeforeEach
    void setUp() {
        codec = new RefIdCodec(SECRET);
        service = new ReferralBindingService(userRepo, codec);
    }

    /** save() 模拟自增主键：每次 persist 分配递增 id（首次注册取自身 id 需要）。 */
    private void stubAutoIncrementSave() {
        AtomicLong seq = new AtomicLong(1000);
        when(userRepo.save(any(H5User.class))).thenAnswer(inv -> {
            H5User u = inv.getArgument(0);
            if (u.getId() == null) {
                setId(u, seq.incrementAndGet());
            }
            return u;
        });
    }

    private static H5User userWithChain(long id, Long inviterId, Long grandInviterId) {
        H5User u = H5User.create("openid-" + id);
        setId(u, id);
        if (inviterId != null) {
            u.bindReferral(inviterId, grandInviterId);
        }
        return u;
    }

    private static void setId(H5User u, long id) {
        try {
            Field f = H5User.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(u, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    // ---- onWxLogin: 仅创建/定位本人，绝不绑定 ----

    @Test
    void onWxLogin_firstLogin_createsUserWithoutBinding() {
        // 首次登录携带（解码）有效 refId 也不再自动绑定 —— 绑定改由 confirmBinding 显式触发。
        when(userRepo.findByOpenid("newbie")).thenReturn(Optional.empty());
        stubAutoIncrementSave();

        H5User result = service.onWxLogin("newbie", "小明", "https://wx.qq.com/a.png");

        assertThat(result.getInviterId()).isNull();
        assertThat(result.getGrandInviterId()).isNull();
        assertThat(result.getNickname()).isEqualTo("小明");        // 资料快照写入
        assertThat(result.getAvatarUrl()).isEqualTo("https://wx.qq.com/a.png");
        verify(userRepo, never()).findById(any());
    }

    @Test
    void onWxLogin_existingUser_chainUntouched_profileRefreshed() {
        H5User existing = userWithChain(777L, 100L, 50L);
        when(userRepo.findByOpenid("veteran")).thenReturn(Optional.of(existing));
        when(userRepo.save(any(H5User.class))).thenAnswer(inv -> inv.getArgument(0));

        H5User result = service.onWxLogin("veteran", "老王", "https://wx.qq.com/b.png");

        assertThat(result.getInviterId()).isEqualTo(100L);   // 关系链不变
        assertThat(result.getGrandInviterId()).isEqualTo(50L);
        assertThat(result.getNickname()).isEqualTo("老王");   // 资料快照刷新
        verify(userRepo, never()).findById(any());
    }

    // ---- confirmBinding: 确认才绑定 L1/L2 ----

    @Test
    void confirmBinding_withValidRefId_bindsL1AndL2() {
        // 邀请人 id=100，其自身 inviter_id=50 → 确认者 grand_inviter_id 应快照为 50。
        H5User self = userWithChain(1001L, null, null);
        when(userRepo.findByOpenid("newbie")).thenReturn(Optional.of(self));
        when(userRepo.findById(100L)).thenReturn(Optional.of(userWithChain(100L, 50L, 25L)));

        boolean bound = service.confirmBinding("newbie", codec.encode(100L));

        assertThat(bound).isTrue();
        assertThat(self.getInviterId()).isEqualTo(100L);     // L1 = 邀请人
        assertThat(self.getGrandInviterId()).isEqualTo(50L); // L2 = 邀请人的 inviter_id（一次性快照）
        verify(userRepo).save(self);
    }

    @Test
    void confirmBinding_inviterHasNoInviter_grandInviterIsNull() {
        H5User self = userWithChain(1001L, null, null);
        when(userRepo.findByOpenid("newbie")).thenReturn(Optional.of(self));
        when(userRepo.findById(100L)).thenReturn(Optional.of(userWithChain(100L, null, null)));

        boolean bound = service.confirmBinding("newbie", codec.encode(100L));

        assertThat(bound).isTrue();
        assertThat(self.getInviterId()).isEqualTo(100L);
        assertThat(self.getGrandInviterId()).isNull();
    }

    @Test
    void confirmBinding_alreadyBound_isIdempotent() {
        // 已绑定用户再次确认：幂等返回 false，绝不覆盖既有关系链、不写库、不查邀请人。
        H5User self = userWithChain(1001L, 100L, 50L);
        when(userRepo.findByOpenid("veteran")).thenReturn(Optional.of(self));

        boolean bound = service.confirmBinding("veteran", codec.encode(999L));

        assertThat(bound).isFalse();
        assertThat(self.getInviterId()).isEqualTo(100L);
        assertThat(self.getGrandInviterId()).isEqualTo(50L);
        verify(userRepo, never()).save(any());
        verify(userRepo, never()).findById(any());
    }

    @Test
    void confirmBinding_forgedRefId_fallsBackToNaturalTraffic() {
        H5User self = userWithChain(1001L, null, null);
        when(userRepo.findByOpenid("newbie")).thenReturn(Optional.of(self));

        // 篡改签名：合法 payload + 伪造 sig，应解码失败 → 不绑定、不报错。
        String forged = codec.encode(100L).split("\\.")[0] + ".AAAA";
        boolean bound = service.confirmBinding("newbie", forged);

        assertThat(bound).isFalse();
        assertThat(self.getInviterId()).isNull();
        assertThat(self.getGrandInviterId()).isNull();
        verify(userRepo, never()).findById(any());
        verify(userRepo, never()).save(any());
    }

    @Test
    void confirmBinding_blankRefId_naturalTraffic() {
        H5User self = userWithChain(1001L, null, null);
        when(userRepo.findByOpenid("newbie")).thenReturn(Optional.of(self));

        boolean bound = service.confirmBinding("newbie", null);

        assertThat(bound).isFalse();
        assertThat(self.getInviterId()).isNull();
        verify(userRepo, never()).findById(any());
        verify(userRepo, never()).save(any());
    }

    @Test
    void confirmBinding_selfInvite_isIgnored() {
        // refId 解出自身 user_id（1001）→ 自我邀请被忽略，不绑定。
        H5User self = userWithChain(1001L, null, null);
        when(userRepo.findByOpenid("selfie")).thenReturn(Optional.of(self));

        boolean bound = service.confirmBinding("selfie", codec.encode(1001L));

        assertThat(bound).isFalse();
        assertThat(self.getInviterId()).isNull();
        assertThat(self.getGrandInviterId()).isNull();
        verify(userRepo, never()).findById(any());
        verify(userRepo, never()).save(any());
    }

    @Test
    void confirmBinding_unknownOpenid_throwsUnauthorized() {
        when(userRepo.findByOpenid("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmBinding("ghost", codec.encode(100L)))
                .isInstanceOf(BizException.class);
        verify(userRepo, never()).findById(any());
    }
}
