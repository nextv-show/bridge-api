package com.sanshuiyuan.user.infra.repository;

import com.sanshuiyuan.user.domain.Role;
import com.sanshuiyuan.user.domain.UserRole;
import com.sanshuiyuan.user.domain.UserRole.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {
    // userId/role 位于 @EmbeddedId UserRoleId 内，派生查询必须穿透 id 前缀
    List<UserRole> findByIdUserId(Long userId);
    boolean existsByIdUserIdAndIdRole(Long userId, Role role);
    void deleteByIdUserIdAndIdRole(Long userId, Role role);
}
