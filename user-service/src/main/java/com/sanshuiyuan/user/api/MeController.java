package com.sanshuiyuan.user.api;

import com.sanshuiyuan.user.api.dto.SwitchRoleRequest;
import com.sanshuiyuan.user.api.dto.UpdateProfileRequest;
import com.sanshuiyuan.user.api.dto.UserInfo;
import com.sanshuiyuan.user.application.RoleService;
import com.sanshuiyuan.user.domain.Role;
import com.sanshuiyuan.user.domain.User;
import com.sanshuiyuan.user.infra.repository.UserRepository;
import com.sanshuiyuan.user.infra.repository.UserRoleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/me")
public class MeController {

    @Value("${app.avatar.dir:/www/avatars}")
    private String avatarDir;
    @Value("${app.avatar.base-url:https://api.sanshuiyuan.com}")
    private String avatarBaseUrl;

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

    /** 上传微信头像（chooseAvatar 临时文件），存到静态目录并写回 avatarUrl。 */
    @PostMapping("/avatar")
    public ResponseEntity<Map<String, String>> uploadAvatar(@AuthenticationPrincipal Long userId,
                                                            @RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "空文件"));
        }
        String orig = file.getOriginalFilename();
        String ext = ".png";
        if (orig != null && orig.contains(".")) {
            String e = orig.substring(orig.lastIndexOf('.')).toLowerCase();
            if (e.matches("\\.(png|jpg|jpeg|webp)")) ext = e;
        }
        String filename = "u" + userId + "-" + UUID.randomUUID().toString().substring(0, 8) + ext;
        Path dir = Path.of(avatarDir);
        Files.createDirectories(dir);
        Files.copy(file.getInputStream(), dir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);

        String url = avatarBaseUrl + "/avatars/" + filename;
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setAvatarUrl(url);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("avatarUrl", url));
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
