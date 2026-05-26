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
        return findOrCreateByUnionid(unionid, openidWx, openidApp, null, null);
    }

    /** 支持关系链参数的创建/查找（供内部 sync 接口调用，见 spec 012）。 */
    public User findOrCreateByUnionid(String unionid, String openidWx, String openidApp,
                                       Long inviterId, Long grandInviterId) {
        return userRepository.findByUnionid(unionid)
                .orElseGet(() -> createUser(unionid, openidWx, openidApp, inviterId, grandInviterId));
    }

    /**
     * 供 sync-h5 内部接口创建用户（spec 012）。H5 openid 落入 openid_wx，openid_app 为空。
     * 复用统一的建号流程（CONSUMER 角色 + 首页布局偏好 + 关系链一次性写入）。
     */
    public User createUserForSync(String unionid, String openidWx,
                                  Long inviterId, Long grandInviterId) {
        return createUser(unionid, openidWx, null, inviterId, grandInviterId);
    }

    private User createUser(String unionid, String openidWx, String openidApp,
                            Long inviterId, Long grandInviterId) {
        User user = new User();
        user.setUnionid(unionid);
        user.setOpenidWx(openidWx);
        user.setOpenidApp(openidApp);
        user.setActiveRole(Role.CONSUMER);
        // 关系链：仅首次注册写入，已注册用户不可改（合规铁律 P0）
        user.setInviterId(inviterId);
        user.setGrandInviterId(grandInviterId);
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
        List<String> roles = userRoleRepository.findByIdUserId(user.getId())
                .stream().map(ur -> ur.getId().getRole().name()).toList();
        UserInfo userInfo = new UserInfo(user.getId(), user.getNickname(), user.getAvatarUrl(),
                user.getActiveRole().name(), roles);
        userInfo.setInviterId(user.getInviterId());
        userInfo.setGrandInviterId(user.getGrandInviterId());
        return new AuthResponse(accessToken, refreshToken, userInfo);
    }
}
