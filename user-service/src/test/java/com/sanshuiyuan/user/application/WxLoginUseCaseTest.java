package com.sanshuiyuan.user.application;

import com.sanshuiyuan.user.api.dto.AuthResponse;
import com.sanshuiyuan.user.api.dto.TokenResponse;
import com.sanshuiyuan.user.domain.HomeLayoutPref;
import com.sanshuiyuan.user.domain.Role;
import com.sanshuiyuan.user.domain.User;
import com.sanshuiyuan.user.domain.UserRole;
import com.sanshuiyuan.user.infra.jwt.JwtIssuer;
import com.sanshuiyuan.user.infra.repository.HomeLayoutPrefRepository;
import com.sanshuiyuan.user.infra.repository.UserRepository;
import com.sanshuiyuan.user.infra.repository.UserRoleRepository;
import com.sanshuiyuan.user.infra.wx.WxMiniProgramClient;
import com.sanshuiyuan.user.infra.wx.WxOpenPlatformClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * B.2.7: WxLoginUseCase unit tests (pure Mockito) covering upsert by unionid and the
 * first-login bootstrap (default CONSUMER role + home layout pref).
 */
@ExtendWith(MockitoExtension.class)
class WxLoginUseCaseTest {

    @Mock UserRepository userRepository;
    @Mock UserRoleRepository userRoleRepository;
    @Mock HomeLayoutPrefRepository homeLayoutPrefRepository;
    @Mock JwtIssuer jwtIssuer;
    @Mock WxMiniProgramClient wxMiniProgramClient;
    @Mock WxOpenPlatformClient wxOpenPlatformClient;

    @InjectMocks WxLoginUseCase useCase;

    @Test
    void loginMiniProgram_newUser_createsUserWithConsumerRoleAndPref() {
        when(wxMiniProgramClient.code2session("js-code"))
                .thenReturn(new WxMiniProgramClient.WxSessionResponse("openid-x", "union-x", "sk"));
        when(userRepository.findByUnionid("union-x")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(55L);
            return u;
        });
        when(userRoleRepository.findByIdUserId(55L)).thenReturn(List.of());
        when(jwtIssuer.issueAccessToken(eq(55L), any(Role.class))).thenReturn("access-jwt");
        when(jwtIssuer.issueRefreshToken(55L)).thenReturn("refresh-jwt");

        AuthResponse resp = useCase.loginMiniProgram("js-code");

        // New user persisted with CONSUMER active role and openidWx set from the mini-program flow.
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User created = userCaptor.getValue();
        assertThat(created.getUnionid()).isEqualTo("union-x");
        assertThat(created.getOpenidWx()).isEqualTo("openid-x");
        assertThat(created.getActiveRole()).isEqualTo(Role.CONSUMER);

        // Default CONSUMER role row created.
        ArgumentCaptor<UserRole> roleCaptor = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleRepository).save(roleCaptor.capture());
        assertThat(roleCaptor.getValue().getId().getRole()).isEqualTo(Role.CONSUMER);
        assertThat(roleCaptor.getValue().getId().getUserId()).isEqualTo(55L);

        // Home layout pref bootstrapped with CONSUMER.
        ArgumentCaptor<HomeLayoutPref> prefCaptor = ArgumentCaptor.forClass(HomeLayoutPref.class);
        verify(homeLayoutPrefRepository).save(prefCaptor.capture());
        assertThat(prefCaptor.getValue().getUserId()).isEqualTo(55L);
        assertThat(prefCaptor.getValue().getActiveRole()).isEqualTo(Role.CONSUMER);

        assertThat(resp.getAccessToken()).isEqualTo("access-jwt");
        assertThat(resp.getRefreshToken()).isEqualTo("refresh-jwt");
        assertThat(resp.getUser().getId()).isEqualTo(55L);
    }

    @Test
    void loginMiniProgram_existingUser_doesNotRecreate() {
        User existing = new User();
        existing.setId(7L);
        existing.setUnionid("union-y");
        existing.setActiveRole(Role.OWNER);

        when(wxMiniProgramClient.code2session("js-code-2"))
                .thenReturn(new WxMiniProgramClient.WxSessionResponse("openid-y", "union-y", "sk"));
        when(userRepository.findByUnionid("union-y")).thenReturn(Optional.of(existing));
        when(userRoleRepository.findByIdUserId(7L)).thenReturn(List.of(
                new UserRole(7L, Role.CONSUMER), new UserRole(7L, Role.OWNER)));
        when(jwtIssuer.issueAccessToken(eq(7L), eq(Role.OWNER))).thenReturn("access-jwt");
        when(jwtIssuer.issueRefreshToken(7L)).thenReturn("refresh-jwt");

        AuthResponse resp = useCase.loginMiniProgram("js-code-2");

        // No new user / role / pref created for an existing unionid.
        verify(userRepository, never()).save(any());
        verify(userRoleRepository, never()).save(any());
        verify(homeLayoutPrefRepository, never()).save(any());

        assertThat(resp.getUser().getId()).isEqualTo(7L);
        assertThat(resp.getUser().getActiveRole()).isEqualTo("OWNER");
        assertThat(resp.getUser().getRoles()).containsExactlyInAnyOrder("CONSUMER", "OWNER");
    }

    @Test
    void loginMiniProgram_nullUnionid_matchesByOpenidWx_doesNotRecreate() {
        // 线上回归：小程序未绑微信开放平台时 jscode2session 不下发 unionid。
        // 老逻辑 findByUnionid(null) 会命中多个 unionid=NULL 的用户抛 NonUniqueResultException → 登录 500。
        // 现按 openid_wx 兜底命中既有用户，不再重复建号、不报错。
        User existing = new User();
        existing.setId(9L);
        existing.setOpenidWx("openid-no-union");
        existing.setActiveRole(Role.CONSUMER);

        when(wxMiniProgramClient.code2session("js-code-3"))
                .thenReturn(new WxMiniProgramClient.WxSessionResponse("openid-no-union", null, "sk"));
        when(userRepository.findByOpenidWx("openid-no-union")).thenReturn(Optional.of(existing));
        when(userRoleRepository.findByIdUserId(9L)).thenReturn(List.of(new UserRole(9L, Role.CONSUMER)));
        when(jwtIssuer.issueAccessToken(eq(9L), eq(Role.CONSUMER))).thenReturn("access-jwt");
        when(jwtIssuer.issueRefreshToken(9L)).thenReturn("refresh-jwt");

        AuthResponse resp = useCase.loginMiniProgram("js-code-3");

        // 不得用 unionid 查询（避免 null 命中多行）；既有用户不重复创建。
        verify(userRepository, never()).findByUnionid(any());
        verify(userRepository, never()).save(any());
        verify(userRoleRepository, never()).save(any());
        verify(homeLayoutPrefRepository, never()).save(any());
        assertThat(resp.getUser().getId()).isEqualTo(9L);
    }

    @Test
    void loginApp_newUser_setsOpenidApp() {
        when(wxOpenPlatformClient.exchangeAppCode("app-code"))
                .thenReturn(new WxOpenPlatformClient.WxOAuthResponse("openid-app", "union-z", "at"));
        when(userRepository.findByUnionid("union-z")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(88L);
            return u;
        });
        when(userRoleRepository.findByIdUserId(88L)).thenReturn(List.of());
        when(jwtIssuer.issueAccessToken(eq(88L), any(Role.class))).thenReturn("a");
        when(jwtIssuer.issueRefreshToken(88L)).thenReturn("r");

        useCase.loginApp("app-code");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User created = userCaptor.getValue();
        assertThat(created.getOpenidApp()).isEqualTo("openid-app");
        assertThat(created.getOpenidWx()).isNull();
        assertThat(created.getActiveRole()).isEqualTo(Role.CONSUMER);
    }

    // ---- F.1: refresh 流回归 ----

    @Test
    void refreshToken_validRefresh_issuesNewTokenPair() {
        User user = new User();
        user.setId(7L);
        user.setActiveRole(Role.OWNER);
        when(jwtIssuer.parseToken("refresh-jwt"))
                .thenReturn(Map.of("type", "refresh", "sub", "7"));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(jwtIssuer.issueAccessToken(7L, Role.OWNER)).thenReturn("new-access");
        when(jwtIssuer.issueRefreshToken(7L)).thenReturn("new-refresh");

        TokenResponse resp = useCase.refreshToken("refresh-jwt");

        assertThat(resp.getAccessToken()).isEqualTo("new-access");
        assertThat(resp.getRefreshToken()).isEqualTo("new-refresh");
    }

    @Test
    void refreshToken_accessTokenUsedAsRefresh_rejected() {
        when(jwtIssuer.parseToken("access-jwt"))
                .thenReturn(Map.of("type", "access", "sub", "7"));

        assertThatThrownBy(() -> useCase.refreshToken("access-jwt"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid refresh token");
        verify(userRepository, never()).findById(any());
    }

    @Test
    void refreshToken_userNotFound_rejected() {
        when(jwtIssuer.parseToken("refresh-jwt"))
                .thenReturn(Map.of("type", "refresh", "sub", "999"));
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.refreshToken("refresh-jwt"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("User not found");
    }
}
