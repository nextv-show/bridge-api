package com.sanshuiyuan.user.api;

import com.sanshuiyuan.user.application.RoleService;
import com.sanshuiyuan.user.application.SyncH5UseCase;
import com.sanshuiyuan.user.domain.User;
import com.sanshuiyuan.user.infra.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * spec 006 Phase A：GET /internal/users/by-openid（供 ess-service 合同 owner 校验解析 userId）。
 * /internal/** 的 S2S 鉴权由生产 S2sTokenFilter 承担（此 slice 用 permissive TestSecurityConfig
 * 替代生产链，专注 controller 的解析行为）。
 */
@WebMvcTest(InternalUserController.class)
@Import(TestSecurityConfig.class)
class InternalUserControllerByOpenidTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean UserRepository userRepository;
    @MockBean RoleService roleService;
    @MockBean SyncH5UseCase syncH5UseCase;

    @Test
    void byOpenid_found_returnsUserId() throws Exception {
        User user = org.mockito.Mockito.mock(User.class);
        when(user.getId()).thenReturn(42L);
        when(userRepository.findByOpenidWx("openid-abc")).thenReturn(Optional.of(user));

        mockMvc.perform(get("/internal/users/by-openid").param("openid", "openid-abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(42));
    }

    @Test
    void byOpenid_notFound_returnsNullUserId() throws Exception {
        when(userRepository.findByOpenidWx("openid-none")).thenReturn(Optional.empty());

        mockMvc.perform(get("/internal/users/by-openid").param("openid", "openid-none"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(org.hamcrest.Matchers.nullValue()));
    }
}
