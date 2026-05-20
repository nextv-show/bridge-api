package com.sanshuiyuan.user.application;

import com.sanshuiyuan.user.domain.HomeLayoutPref;
import com.sanshuiyuan.user.domain.Role;
import com.sanshuiyuan.user.domain.User;
import com.sanshuiyuan.user.domain.UserRole;
import com.sanshuiyuan.user.infra.repository.HomeLayoutPrefRepository;
import com.sanshuiyuan.user.infra.repository.UserRepository;
import com.sanshuiyuan.user.infra.repository.UserRoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RoleService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final HomeLayoutPrefRepository homeLayoutPrefRepository;

    public RoleService(UserRepository userRepository,
                       UserRoleRepository userRoleRepository,
                       HomeLayoutPrefRepository homeLayoutPrefRepository) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.homeLayoutPrefRepository = homeLayoutPrefRepository;
    }

    public List<Role> getRoles(Long userId) {
        return userRoleRepository.findByIdUserId(userId)
                .stream().map(ur -> ur.getId().getRole()).toList();
    }

    @Transactional
    public void addRole(Long userId, Role role) {
        if (!userRoleRepository.existsByIdUserIdAndIdRole(userId, role)) {
            userRoleRepository.save(new UserRole(userId, role));
        }
    }

    @Transactional
    public void switchActiveRole(Long userId, Role role) {
        if (!userRoleRepository.existsByIdUserIdAndIdRole(userId, role)) {
            throw new RuntimeException("User does not have role: " + role.name());
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setActiveRole(role);
        userRepository.save(user);

        HomeLayoutPref pref = homeLayoutPrefRepository.findByUserId(userId).orElse(null);
        if (pref == null) {
            pref = new HomeLayoutPref();
            pref.setUserId(userId);
        }
        pref.setActiveRole(role);
        homeLayoutPrefRepository.save(pref);
    }
}
