package com.sanshuiyuan.admin.api;

import com.sanshuiyuan.admin.api.dto.LoginRequest;
import com.sanshuiyuan.admin.config.SecurityConfig;
import com.sanshuiyuan.admin.domain.AdminUser;
import com.sanshuiyuan.admin.infra.repository.AdminUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/admin/auth")
public class AdminAuthController {

    private final AdminUserRepository userRepo;
    private final PasswordEncoder encoder;
    private final SecurityConfig.AdminJwtUtil jwtUtil;

    public AdminAuthController(AdminUserRepository userRepo,
                               PasswordEncoder encoder,
                               SecurityConfig.AdminJwtUtil jwtUtil) {
        this.userRepo = userRepo;
        this.encoder = encoder;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest req) {
        var user = userRepo.findByUsername(req.username())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误"));
        if (!user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "账号已禁用");
        }
        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole().name());
        return Map.of(
            "token", token,
            "username", user.getUsername(),
            "role", user.getRole().name()
        );
    }
}
