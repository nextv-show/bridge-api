package com.sanshuiyuan.user.infra.repository;

import com.sanshuiyuan.user.AbstractMysqlContainerTest;
import com.sanshuiyuan.user.domain.Role;
import com.sanshuiyuan.user.domain.User;
import com.sanshuiyuan.user.domain.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B.1.3 (fix verification): UserRoleRepository derived queries against real MySQL
 * (Testcontainers + Flyway).
 *
 * Defect #3 (now FIXED): userId/role live inside the {@code @EmbeddedId UserRoleId}, so the
 * derived queries must traverse the embedded id (findByIdUserId / existsByIdUserIdAndIdRole /
 * deleteByIdUserIdAndIdRole). Previously they omitted the {@code Id} prefix and the repository
 * bean could not be created, failing context startup with
 * "No property 'userId' found for type 'UserRole'".
 *
 * This slice loads UserRoleRepository normally (no exclusion) and proves the three renamed
 * methods query/delete correctly against the production schema (composite PK, ENUM column, FK).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRoleRepositoryIT extends AbstractMysqlContainerTest {

    @Autowired
    UserRepository userRepository;

    @Autowired
    UserRoleRepository userRoleRepository;

    private Long persistUser(String unionid) {
        User u = new User();
        u.setUnionid(unionid);
        u.setActiveRole(Role.CONSUMER);
        return userRepository.saveAndFlush(u).getId();
    }

    @Test
    void findByIdUserId_returnsAllRolesForUser() {
        Long userId = persistUser("ur-find");
        userRoleRepository.save(new UserRole(userId, Role.CONSUMER));
        userRoleRepository.save(new UserRole(userId, Role.OWNER));
        userRoleRepository.flush();

        List<UserRole> roles = userRoleRepository.findByIdUserId(userId);

        assertThat(roles).hasSize(2);
        assertThat(roles).extracting(ur -> ur.getId().getRole())
                .containsExactlyInAnyOrder(Role.CONSUMER, Role.OWNER);
        assertThat(roles).allSatisfy(ur ->
                assertThat(ur.getId().getUserId()).isEqualTo(userId));
        assertThat(userRoleRepository.findByIdUserId(999_999L)).isEmpty();
    }

    @Test
    void existsByIdUserIdAndIdRole_reflectsMembership() {
        Long userId = persistUser("ur-exists");
        userRoleRepository.save(new UserRole(userId, Role.OWNER));
        userRoleRepository.flush();

        assertThat(userRoleRepository.existsByIdUserIdAndIdRole(userId, Role.OWNER)).isTrue();
        assertThat(userRoleRepository.existsByIdUserIdAndIdRole(userId, Role.PROMOTER)).isFalse();
        assertThat(userRoleRepository.existsByIdUserIdAndIdRole(999_999L, Role.OWNER)).isFalse();
    }

    @Test
    void deleteByIdUserIdAndIdRole_removesOnlyMatchingRow() {
        Long userId = persistUser("ur-delete");
        userRoleRepository.save(new UserRole(userId, Role.CONSUMER));
        userRoleRepository.save(new UserRole(userId, Role.OWNER));
        userRoleRepository.flush();

        userRoleRepository.deleteByIdUserIdAndIdRole(userId, Role.OWNER);
        userRoleRepository.flush();

        List<UserRole> remaining = userRoleRepository.findByIdUserId(userId);
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getId().getRole()).isEqualTo(Role.CONSUMER);
        assertThat(userRoleRepository.existsByIdUserIdAndIdRole(userId, Role.OWNER)).isFalse();
    }
}
