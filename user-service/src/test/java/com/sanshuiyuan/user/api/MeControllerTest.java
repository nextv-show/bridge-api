package com.sanshuiyuan.user.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanshuiyuan.user.application.RoleService;
import com.sanshuiyuan.user.domain.Role;
import com.sanshuiyuan.user.domain.User;
import com.sanshuiyuan.user.domain.UserRole;
import com.sanshuiyuan.user.infra.repository.UserRepository;
import com.sanshuiyuan.user.infra.repository.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * B.3.1 / B.3.2: MeController. A permissive TestSecurityConfig replaces the production filter
 * chain (not imported by @WebMvcTest) while keeping the SecurityContextHolderFilter so the
 * authentication() post-processor seeds a Long principal for @AuthenticationPrincipal.
 */
@WebMvcTest(MeController.class)
@Import(TestSecurityConfig.class)
class MeControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean UserRepository userRepository;
    @MockBean UserRoleRepository userRoleRepository;
    @MockBean RoleService roleService;

    private RequestPostProcessor principal(Long userId) {
        return authentication(new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_CONSUMER"))));
    }

    @Test
    void me_returnsUserInfoWithRoles() throws Exception {
        User user = new User();
        user.setId(7L);
        user.setNickname("张三");
        user.setActiveRole(Role.OWNER);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userRoleRepository.findByIdUserId(7L)).thenReturn(List.of(
                new UserRole(7L, Role.CONSUMER), new UserRole(7L, Role.OWNER)));

        mockMvc.perform(get("/me").with(principal(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.nickname").value("张三"))
                .andExpect(jsonPath("$.activeRole").value("OWNER"))
                .andExpect(jsonPath("$.roles.length()").value(2));
    }

    @Test
    void switchActiveRole_delegatesToRoleService() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("role", "OWNER"));

        mockMvc.perform(put("/me/active-role").with(principal(7L))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        verify(roleService).switchActiveRole(eq(7L), eq(Role.OWNER));
    }
}
