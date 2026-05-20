package com.sanshuiyuan.user.application;

import com.sanshuiyuan.user.domain.HomeLayoutPref;
import com.sanshuiyuan.user.domain.Role;
import com.sanshuiyuan.user.domain.User;
import com.sanshuiyuan.user.infra.repository.HomeLayoutPrefRepository;
import com.sanshuiyuan.user.infra.repository.UserRepository;
import com.sanshuiyuan.user.infra.repository.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * B.3.2 / B.3.3: RoleService unit tests covering idempotent addRole and active-role switching
 * with membership validation.
 */
@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock UserRepository userRepository;
    @Mock UserRoleRepository userRoleRepository;
    @Mock HomeLayoutPrefRepository homeLayoutPrefRepository;

    @InjectMocks RoleService roleService;

    @Test
    void addRole_whenAbsent_saves() {
        when(userRoleRepository.existsByIdUserIdAndIdRole(7L, Role.OWNER)).thenReturn(false);
        roleService.addRole(7L, Role.OWNER);
        verify(userRoleRepository).save(any());
    }

    @Test
    void addRole_whenPresent_isIdempotent() {
        when(userRoleRepository.existsByIdUserIdAndIdRole(7L, Role.OWNER)).thenReturn(true);
        roleService.addRole(7L, Role.OWNER);
        verify(userRoleRepository, never()).save(any());
    }

    @Test
    void switchActiveRole_userLacksRole_throws() {
        when(userRoleRepository.existsByIdUserIdAndIdRole(7L, Role.PROMOTER)).thenReturn(false);
        assertThatThrownBy(() -> roleService.switchActiveRole(7L, Role.PROMOTER))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("does not have role");
        verify(userRepository, never()).save(any());
    }

    @Test
    void switchActiveRole_valid_updatesUserAndPref() {
        User user = new User();
        user.setId(7L);
        user.setActiveRole(Role.CONSUMER);
        when(userRoleRepository.existsByIdUserIdAndIdRole(7L, Role.OWNER)).thenReturn(true);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(homeLayoutPrefRepository.findByUserId(7L)).thenReturn(Optional.empty());

        roleService.switchActiveRole(7L, Role.OWNER);

        verify(userRepository).save(argThat(u -> u.getActiveRole() == Role.OWNER));
        verify(homeLayoutPrefRepository).save(argThat((HomeLayoutPref p) ->
                p.getUserId().equals(7L) && p.getActiveRole() == Role.OWNER));
    }
}
