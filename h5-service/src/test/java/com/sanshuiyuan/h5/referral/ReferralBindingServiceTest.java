package com.sanshuiyuan.h5.referral;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T8b.2/T8b.3：H5 关系链绑定服务单测 —— 首次注册绑定 L1/L2、解码失败/自我邀请降级、已注册不触碰。
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

    // ---- T8b.2: 首次注册绑定 L1/L2 ----

    @Test
    void firstRegister_withValidRefId_bindsL1AndL2() {
        // 邀请人 id=100，其自身 inviter_id=50 → 新用户 grand_inviter_id 应快照为 50。
        when(userRepo.findByOpenid("newbie")).thenReturn(Optional.empty());
        when(userRepo.findById(100L)).thenReturn(Optional.of(userWithChain(100L, 50L, 25L)));
        stubAutoIncrementSave();

        H5User result = service.onWxLogin("newbie", codec.encode(100L));

        assertThat(result.getInviterId()).isEqualTo(100L);   // L1 = 邀请人
        assertThat(result.getGrandInviterId()).isEqualTo(50L); // L2 = 邀请人的 inviter_id（一次性快照）
    }

    @Test
    void firstRegister_inviterHasNoInviter_grandInviterIsNull() {
        when(userRepo.findByOpenid("newbie")).thenReturn(Optional.empty());
        when(userRepo.findById(100L)).thenReturn(Optional.of(userWithChain(100L, null, null)));
        stubAutoIncrementSave();

        H5User result = service.onWxLogin("newbie", codec.encode(100L));

        assertThat(result.getInviterId()).isEqualTo(100L);
        assertThat(result.getGrandInviterId()).isNull();
    }

    // ---- T8b.3: 降级与防护 ----

    @Test
    void firstRegister_forgedRefId_fallsBackToNaturalTraffic() {
        when(userRepo.findByOpenid("newbie")).thenReturn(Optional.empty());
        stubAutoIncrementSave();

        // 篡改签名：合法 payload + 伪造 sig，应解码失败 → 自然流量（不报错、不绑定）。
        String forged = codec.encode(100L).split("\\.")[0] + ".AAAA";
        H5User result = service.onWxLogin("newbie", forged);

        assertThat(result.getInviterId()).isNull();
        assertThat(result.getGrandInviterId()).isNull();
        verify(userRepo, never()).findById(any());
    }

    @Test
    void firstRegister_blankRefId_naturalTraffic() {
        when(userRepo.findByOpenid("newbie")).thenReturn(Optional.empty());
        stubAutoIncrementSave();

        H5User result = service.onWxLogin("newbie", null);

        assertThat(result.getInviterId()).isNull();
        assertThat(result.getGrandInviterId()).isNull();
        verify(userRepo, never()).findById(any());
    }

    @Test
    void firstRegister_selfInvite_isIgnored() {
        // 新用户自增 id 将是 1001（stubAutoIncrementSave 从 1000 起 incrementAndGet）。
        when(userRepo.findByOpenid("selfie")).thenReturn(Optional.empty());
        stubAutoIncrementSave();

        H5User result = service.onWxLogin("selfie", codec.encode(1001L));

        assertThat(result.getId()).isEqualTo(1001L);
        assertThat(result.getInviterId()).isNull();   // 自我邀请被忽略
        assertThat(result.getGrandInviterId()).isNull();
        verify(userRepo, never()).findById(any());
    }

    @Test
    void existingUser_carryingRefId_chainUntouched() {
        // 已注册用户携带 refId 再次登录：返回原记录，关系链不变，且不写库。
        H5User existing = userWithChain(777L, 100L, 50L);
        when(userRepo.findByOpenid("veteran")).thenReturn(Optional.of(existing));

        H5User result = service.onWxLogin("veteran", codec.encode(999L));

        assertThat(result.getInviterId()).isEqualTo(100L);
        assertThat(result.getGrandInviterId()).isEqualTo(50L);
        verify(userRepo, never()).save(any());
        verify(userRepo, never()).findById(any());
    }
}
