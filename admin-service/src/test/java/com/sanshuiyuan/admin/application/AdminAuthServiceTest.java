package com.sanshuiyuan.admin.application;

import com.sanshuiyuan.admin.api.AdminAuthController;
import com.sanshuiyuan.admin.api.dto.LoginRequest;
import com.sanshuiyuan.admin.config.SecurityConfig;
import com.sanshuiyuan.admin.domain.AdminUser;
import com.sanshuiyuan.admin.infra.repository.AdminUserRepository;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminAuthServiceTest {

    @Mock private AdminUserRepository userRepo;
    @Mock private PasswordEncoder encoder;
    private SecurityConfig.AdminJwtUtil jwtUtil;
    private AdminAuthController controller;

    @BeforeEach
    void setUp() {
        jwtUtil = new SecurityConfig.AdminJwtUtil("test-secret-key-at-least-32-bytes-long!", 8);
        controller = new AdminAuthController(userRepo, encoder, jwtUtil);
    }

    private AdminUser createAdminUser(Long id, String username, AdminUser.Role role,
                                       boolean enabled, String passwordHash) {
        AdminUser user = new AdminUser();
        setField(user, "id", id);
        setField(user, "username", username);
        setField(user, "role", role);
        setField(user, "enabled", enabled);
        setField(user, "passwordHash", passwordHash);
        return user;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void login_success_returnsJwt() {
        AdminUser user = createAdminUser(1L, "admin", AdminUser.Role.SUPER_ADMIN,
                true, "$2a$10$hashedpassword");

        when(userRepo.findByUsername("admin")).thenReturn(Optional.of(user));
        when(encoder.matches("password123", "$2a$10$hashedpassword")).thenReturn(true);

        var result = controller.login(new LoginRequest("admin", "password123"));

        assertNotNull(result);
        assertEquals("admin", result.get("username"));
        assertEquals("SUPER_ADMIN", result.get("role"));
        String token = (String) result.get("token");
        assertNotNull(token);
        assertFalse(token.isBlank());

        // Verify token is parseable
        JWTClaimsSet claims = jwtUtil.parse(token);
        assertNotNull(claims);
        assertEquals("1", claims.getSubject());
        assertEquals("admin", claims.getClaim("username"));
        assertEquals("SUPER_ADMIN", claims.getClaim("role"));
    }

    @Test
    void login_wrongPassword_rejected() {
        AdminUser user = createAdminUser(1L, "admin", AdminUser.Role.SUPER_ADMIN,
                true, "$2a$10$hashedpassword");

        when(userRepo.findByUsername("admin")).thenReturn(Optional.of(user));
        when(encoder.matches("wrongpass", "$2a$10$hashedpassword")).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                controller.login(new LoginRequest("admin", "wrongpass")));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void login_disabledAccount_rejected() {
        AdminUser user = createAdminUser(1L, "admin", AdminUser.Role.READONLY,
                false, "$2a$10$hashedpassword");

        when(userRepo.findByUsername("admin")).thenReturn(Optional.of(user));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                controller.login(new LoginRequest("admin", "password123")));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void login_userNotFound_rejected() {
        when(userRepo.findByUsername("unknown")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                controller.login(new LoginRequest("unknown", "password123")));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }
}
