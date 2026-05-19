package com.sanshuiyuan.user.infra.repository;

import com.sanshuiyuan.user.domain.Role;
import com.sanshuiyuan.user.domain.UserRole;
import com.sanshuiyuan.user.domain.UserRole.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {
    List<UserRole> findByUserId(Long userId);
    boolean existsByUserIdAndRole(Long userId, Role role);
    void deleteByUserIdAndRole(Long userId, Role role);
}
