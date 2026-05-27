package com.sanshuiyuan.user.api;

import com.sanshuiyuan.user.api.dto.SwitchRoleRequest;
import com.sanshuiyuan.user.api.dto.UpdateProfileRequest;
import com.sanshuiyuan.user.api.dto.UserInfo;
import com.sanshuiyuan.user.application.RoleService;
import com.sanshuiyuan.user.domain.Role;
import com.sanshuiyuan.user.domain.User;
import com.sanshuiyuan.user.infra.repository.UserRepository;
import com.sanshuiyuan.user.infra.repository.UserRoleRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/me")
public class MeController {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleService roleService;

    public MeController(UserRepository userRepository, UserRoleRepository userRoleRepository, RoleService roleService) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleService = roleService;
    }

    @GetMapping
    public ResponseEntity<UserInfo> me(@AuthenticationPrincipal Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ResponseEntity.ok(toInfo(user));
    }

    /** 小程序「头像昵称填写组件」回传昵称/头像后更新资料。 */
    @PutMapping("/profile")
    public ResponseEntity<UserInfo> updateProfile(@AuthenticationPrincipal Long userId,
                                                  @RequestBody UpdateProfileRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (req.nickname() != null && !req.nickname().isBlank()) {
            user.setNickname(req.nickname().trim());
        }
        if (req.avatarUrl() != null && !req.avatarUrl().isBlank()) {
            user.setAvatarUrl(req.avatarUrl().trim());
        }
        userRepository.save(user);
        return ResponseEntity.ok(toInfo(user));
    }

    private UserInfo toInfo(User user) {
        List<String> roles = userRoleRepository.findByIdUserId(user.getId())
                .stream().map(ur -> ur.getId().getRole().name()).toList();
        return new UserInfo(user.getId(), user.getNickname(), user.getAvatarUrl(),
                user.getActiveRole().name(), roles);
    }

    @PutMapping("/active-role")
    public ResponseEntity<Void> switchActiveRole(@AuthenticationPrincipal Long userId,
                                                  @RequestBody SwitchRoleRequest request) {
        roleService.switchActiveRole(userId, Role.valueOf(request.getRole()));
        return ResponseEntity.ok().build();
    }
}
