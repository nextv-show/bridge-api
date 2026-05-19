package com.sanshuiyuan.user.application;

import com.sanshuiyuan.user.api.dto.AuthResponse;
import com.sanshuiyuan.user.api.dto.TokenResponse;
import com.sanshuiyuan.user.api.dto.UserInfo;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WxLoginUseCase {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final HomeLayoutPrefRepository homeLayoutPrefRepository;
    private final JwtIssuer jwtIssuer;
    private final WxMiniProgramClient wxMiniProgramClient;
    private final WxOpenPlatformClient wxOpenPlatformClient;

    public WxLoginUseCase(UserRepository userRepository,
                          UserRoleRepository userRoleRepository,
                          HomeLayoutPrefRepository homeLayoutPrefRepository,
                          JwtIssuer jwtIssuer,
                          WxMiniProgramClient wxMiniProgramClient,
                          WxOpenPlatformClient wxOpenPlatformClient) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.homeLayoutPrefRepository = homeLayoutPrefRepository;
        this.jwtIssuer = jwtIssuer;
        this.wxMiniProgramClient = wxMiniProgramClient;
        this.wxOpenPlatformClient = wxOpenPlatformClient;
    }

    @Transactional
    public AuthResponse loginMiniProgram(String jsCode) {
        var session = wxMiniProgramClient.code2session(jsCode);
        User user = findOrCreateByUnionid(session.unionid(), session.openid(), null);
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse loginApp(String wxAuthCode) {
        var oauth = wxOpenPlatformClient.exchangeAppCode(wxAuthCode);
        User user = findOrCreateByUnionid(oauth.unionid(), null, oauth.openid());
        return buildAuthResponse(user);
    }

    @Transactional
    public TokenResponse refreshToken(String refreshToken) {
        var claims = jwtIssuer.parseToken(refreshToken);
        if (!"refresh".equals(claims.get("type"))) {
            throw new RuntimeException("Invalid refresh token");
        }
        Long userId = Long.valueOf((String) claims.get("sub"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        String newAccess = jwtIssuer.issueAccessToken(user.getId(), user.getActiveRole());
        String newRefresh = jwtIssuer.issueRefreshToken(user.getId());
        return new TokenResponse(newAccess, newRefresh);
    }

    private User findOrCreateByUnionid(String unionid, String openidWx, String openidApp) {
        return userRepository.findByUnionid(unionid)
                .orElseGet(() -> createUser(unionid, openidWx, openidApp));
    }

    private User createUser(String unionid, String openidWx, String openidApp) {
        User user = new User();
        user.setUnionid(unionid);
        user.setOpenidWx(openidWx);
        user.setOpenidApp(openidApp);
        user.setActiveRole(Role.CONSUMER);
        user = userRepository.save(user);

        userRoleRepository.save(new UserRole(user.getId(), Role.CONSUMER));

        HomeLayoutPref pref = new HomeLayoutPref();
        pref.setUserId(user.getId());
        pref.setActiveRole(Role.CONSUMER);
        homeLayoutPrefRepository.save(pref);

        return user;
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtIssuer.issueAccessToken(user.getId(), user.getActiveRole());
        String refreshToken = jwtIssuer.issueRefreshToken(user.getId());
        List<String> roles = userRoleRepository.findByUserId(user.getId())
                .stream().map(ur -> ur.getId().getRole().name()).toList();
        UserInfo userInfo = new UserInfo(user.getId(), user.getNickname(), user.getAvatarUrl(),
                user.getActiveRole().name(), roles);
        return new AuthResponse(accessToken, refreshToken, userInfo);
    }
}
