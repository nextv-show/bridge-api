package com.sanshuiyuan.user.application;

import com.sanshuiyuan.user.api.dto.SyncH5Response;
import com.sanshuiyuan.user.domain.User;
import com.sanshuiyuan.user.infra.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * spec 012 T12.5：sync-h5 同步逻辑单测 —— unionid/openid 命中、未命中建号、关系链派生、
 * 邀请人降级、幂等。合规铁律：命中已存在用户绝不改写关系链。
 */
@ExtendWith(MockitoExtension.class)
class SyncH5UseCaseTest {

    @Mock UserRepository userRepository;
    @Mock WxLoginUseCase wxLoginUseCase;

    @InjectMocks SyncH5UseCase useCase;

    private static User userWith(Long id, Long inviterId) {
        User u = new User();
        u.setId(id);
        u.setInviterId(inviterId);
        return u;
    }

    @Test
    void unionidHit_supplementsMissingOpenid_keepsChain() {
        User existing = userWith(7L, 3L); // 已绑定 L1=3
        existing.setOpenidWx(null);
        when(userRepository.findByUnionid("union-a")).thenReturn(Optional.of(existing));

        SyncH5Response resp = useCase.sync("openid-a", "union-a", 99L);

        // 命中：补齐缺失 openid，但绝不改写关系链（inviterId 仍为 3）。
        assertThat(existing.getOpenidWx()).isEqualTo("openid-a");
        assertThat(existing.getInviterId()).isEqualTo(3L);
        verify(userRepository).save(existing);
        verify(wxLoginUseCase, never()).createUserForSync(any(), any(), any(), any());
        assertThat(resp.userId()).isEqualTo(7L);
        assertThat(resp.isNew()).isFalse();
        assertThat(resp.inviterBound()).isTrue();
    }

    @Test
    void unionidHit_existingOpenid_noSave() {
        User existing = userWith(8L, null);
        existing.setOpenidWx("openid-old");
        when(userRepository.findByUnionid("union-b")).thenReturn(Optional.of(existing));

        SyncH5Response resp = useCase.sync("openid-new", "union-b", null);

        assertThat(existing.getOpenidWx()).isEqualTo("openid-old"); // 不覆盖已有 openid
        verify(userRepository, never()).save(any());
        assertThat(resp.isNew()).isFalse();
        assertThat(resp.inviterBound()).isFalse();
    }

    @Test
    void noUnionid_openidHit_returnsExisting() {
        User existing = userWith(9L, 4L);
        when(userRepository.findByOpenidWx("openid-c")).thenReturn(Optional.of(existing));

        SyncH5Response resp = useCase.sync("openid-c", null, 5L);

        // 幂等：按 openid 命中即返回，不建号、不改链。
        verify(wxLoginUseCase, never()).createUserForSync(any(), any(), any(), any());
        verify(userRepository, never()).save(any());
        assertThat(resp.userId()).isEqualTo(9L);
        assertThat(resp.isNew()).isFalse();
        assertThat(resp.inviterBound()).isTrue();
    }

    @Test
    void notFound_createsNewUser_derivesGrandInviterFromInviter() {
        when(userRepository.findByUnionid("union-d")).thenReturn(Optional.empty());
        User inviter = userWith(10L, 2L); // 邀请人的 inviter_id = 2 → 即新用户的 L2
        when(userRepository.findById(10L)).thenReturn(Optional.of(inviter));
        when(wxLoginUseCase.createUserForSync("union-d", "openid-d", 10L, 2L))
                .thenReturn(userWith(100L, 10L));

        SyncH5Response resp = useCase.sync("openid-d", "union-d", 10L);

        // L1=inviterId=10，L2=邀请人的 inviter_id=2（一次性快照）。
        verify(wxLoginUseCase).createUserForSync("union-d", "openid-d", 10L, 2L);
        assertThat(resp.userId()).isEqualTo(100L);
        assertThat(resp.isNew()).isTrue();
        assertThat(resp.inviterBound()).isTrue();
    }

    @Test
    void notFound_inviterNotExist_fallsBackToNaturalTraffic() {
        when(userRepository.findByUnionid("union-e")).thenReturn(Optional.empty());
        when(userRepository.findById(999L)).thenReturn(Optional.empty());
        when(wxLoginUseCase.createUserForSync(eq("union-e"), eq("openid-e"), isNull(), isNull()))
                .thenReturn(userWith(101L, null));

        SyncH5Response resp = useCase.sync("openid-e", "union-e", 999L);

        // 邀请人不存在 → 不绑定关系链（自然流量）。
        verify(wxLoginUseCase).createUserForSync("union-e", "openid-e", null, null);
        assertThat(resp.isNew()).isTrue();
        assertThat(resp.inviterBound()).isFalse();
    }

    @Test
    void notFound_noInviterId_createsNaturalTraffic() {
        when(userRepository.findByUnionid("union-f")).thenReturn(Optional.empty());
        when(wxLoginUseCase.createUserForSync(eq("union-f"), eq("openid-f"), isNull(), isNull()))
                .thenReturn(userWith(102L, null));

        SyncH5Response resp = useCase.sync("openid-f", "union-f", null);

        verify(userRepository, never()).findById(any());
        verify(wxLoginUseCase).createUserForSync("union-f", "openid-f", null, null);
        assertThat(resp.isNew()).isTrue();
        assertThat(resp.inviterBound()).isFalse();
    }

    @Test
    void noUnionid_notFound_createsByOpenid() {
        when(userRepository.findByOpenidWx("openid-g")).thenReturn(Optional.empty());
        when(wxLoginUseCase.createUserForSync(isNull(), eq("openid-g"), isNull(), isNull()))
                .thenReturn(userWith(103L, null));

        SyncH5Response resp = useCase.sync("openid-g", null, null);

        verify(userRepository, never()).findByUnionid(any());
        verify(wxLoginUseCase).createUserForSync(null, "openid-g", null, null);
        assertThat(resp.isNew()).isTrue();
    }
}
