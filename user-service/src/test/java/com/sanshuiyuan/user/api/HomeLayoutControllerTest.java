package com.sanshuiyuan.user.api;

import com.sanshuiyuan.user.application.HomeLayoutAssembler;
import com.sanshuiyuan.user.domain.Role;
import com.sanshuiyuan.user.domain.User;
import com.sanshuiyuan.user.infra.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * B.3.5: HomeLayoutController. Uses the real HomeLayoutAssembler so the returned section DSL is
 * asserted; UserRepository is mocked to resolve the active-role-derived layout path.
 */
@WebMvcTest(HomeLayoutController.class)
@Import({TestSecurityConfig.class, HomeLayoutControllerTest.Beans.class})
class HomeLayoutControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    UserRepository userRepository;

    @org.springframework.boot.test.context.TestConfiguration
    static class Beans {
        @org.springframework.context.annotation.Bean
        HomeLayoutAssembler homeLayoutAssembler() {
            return new HomeLayoutAssembler();
        }
    }

    private RequestPostProcessor principal(Long userId) {
        return authentication(new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_CONSUMER"))));
    }

    @Test
    void getLayout_explicitRole_returnsOwnerSections() throws Exception {
        mockMvc.perform(get("/home/layout").param("role", "OWNER").with(principal(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections.length()").value(3))
                .andExpect(jsonPath("$.sections[0].key").value("my_assets"));
    }

    @Test
    void getLayout_noRole_usesActiveRoleFromUser() throws Exception {
        User user = new User();
        user.setId(7L);
        user.setActiveRole(Role.CONSUMER);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/home/layout").with(principal(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections[0].key").value("scan_water"));

        verify(userRepository).findById(eq(7L));
    }
}
