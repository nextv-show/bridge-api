package com.sanshuiyuan.user.referral;

import com.sanshuiyuan.user.domain.User;
import com.sanshuiyuan.user.infra.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T14.1：小程序关系链绑定服务单测（自 h5-service 移植，改用 userId 定位 + canonical User 实体）。
 * <ul>
 *   <li><b>登录不绑</b>：本服务不参与登录链路；登录由 {@code WxLoginUseCase} 定位/创建本人，绝不绑定。</li>
 *   <li>{@code confirmBinding} 仅在用户显式确认后绑定 L1/L2：确认才绑定、已绑定幂等、解码失败/自我邀请降级。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ReferralBindingServiceTest {

    private static final String SECRET = "unit-test-referral-secret-0123456789";

    @Mock UserRepository userRepo;

    private RefIdCodec codec;
    private ReferralBindingService service;

    @BeforeEach
    void setUp() {
        codec = new RefIdCodec(SECRET);
        service = new ReferralBindingService(userRepo, codec);
    }

    private static User userWithChain(long id, Long inviterId, Long grandInviterId) {
        User u = new User();
        u.setId(id);
        u.setInviterId(inviterId);
        u.setGrandInviterId(grandInviterId);
        return u;
    }

    // ---- confirmBinding: 确认才绑定 L1/L2 ----

    @Test
    void confirmBinding_withValidRefId_bindsL1AndL2() {
        // 邀请人 id=100，其自身 inviter_id=50 → 确认者 grand_inviter_id 应快照为 50。
        User self = userWithChain(1001L, null, null);
        when(userRepo.findById(1001L)).thenReturn(Optional.of(self));
        when(userRepo.findById(100L)).thenReturn(Optional.of(userWithChain(100L, 50L, 25L)));

        boolean bound = service.confirmBinding(1001L, codec.encode(100L));

        assertThat(bound).isTrue();
        assertThat(self.getInviterId()).isEqualTo(100L);     // L1 = 邀请人
        assertThat(self.getGrandInviterId()).isEqualTo(50L); // L2 = 邀请人的 inviter_id（一次性快照）
        verify(userRepo).save(self);
    }

    @Test
    void confirmBinding_inviterHasNoInviter_grandInviterIsNull() {
        User self = userWithChain(1001L, null, null);
        when(userRepo.findById(1001L)).thenReturn(Optional.of(self));
        when(userRepo.findById(100L)).thenReturn(Optional.of(userWithChain(100L, null, null)));

        boolean bound = service.confirmBinding(1001L, codec.encode(100L));

        assertThat(bound).isTrue();
        assertThat(self.getInviterId()).isEqualTo(100L);
        assertThat(self.getGrandInviterId()).isNull();
    }

    @Test
    void confirmBinding_alreadyBound_isIdempotent() {
        // 已绑定用户再次确认：幂等返回 false，绝不覆盖既有关系链、不写库、不查邀请人。
        User self = userWithChain(1001L, 100L, 50L);
        when(userRepo.findById(1001L)).thenReturn(Optional.of(self));

        boolean bound = service.confirmBinding(1001L, codec.encode(999L));

        assertThat(bound).isFalse();
        assertThat(self.getInviterId()).isEqualTo(100L);
        assertThat(self.getGrandInviterId()).isEqualTo(50L);
        verify(userRepo, never()).save(any());
        verify(userRepo, never()).findById(999L);
    }

    @Test
    void confirmBinding_forgedRefId_fallsBackToNaturalTraffic() {
        User self = userWithChain(1001L, null, null);
        when(userRepo.findById(1001L)).thenReturn(Optional.of(self));

        // 篡改签名：合法 payload + 伪造 sig，应解码失败 → 不绑定、不报错。
        String forged = codec.encode(100L).split("\\.")[0] + ".AAAA";
        boolean bound = service.confirmBinding(1001L, forged);

        assertThat(bound).isFalse();
        assertThat(self.getInviterId()).isNull();
        assertThat(self.getGrandInviterId()).isNull();
        verify(userRepo, never()).findById(100L);
        verify(userRepo, never()).save(any());
    }

    @Test
    void confirmBinding_blankRefId_naturalTraffic() {
        User self = userWithChain(1001L, null, null);
        when(userRepo.findById(1001L)).thenReturn(Optional.of(self));

        boolean bound = service.confirmBinding(1001L, null);

        assertThat(bound).isFalse();
        assertThat(self.getInviterId()).isNull();
        verify(userRepo, never()).save(any());
    }

    @Test
    void confirmBinding_selfInvite_isIgnored() {
        // refId 解出自身 user_id（1001）→ 自我邀请被忽略，不绑定。
        User self = userWithChain(1001L, null, null);
        when(userRepo.findById(1001L)).thenReturn(Optional.of(self));

        boolean bound = service.confirmBinding(1001L, codec.encode(1001L));

        assertThat(bound).isFalse();
        assertThat(self.getInviterId()).isNull();
        assertThat(self.getGrandInviterId()).isNull();
        verify(userRepo, never()).save(any());
    }

    @Test
    void confirmBinding_unknownUser_throws() {
        when(userRepo.findById(9999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmBinding(9999L, codec.encode(100L)))
                .isInstanceOf(RuntimeException.class);
        verify(userRepo, never()).findById(100L);
        verify(userRepo, never()).save(any());
    }
}
