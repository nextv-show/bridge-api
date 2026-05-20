package com.sanshuiyuan.user.infra.repository;

import com.sanshuiyuan.user.AbstractMysqlContainerTest;
import com.sanshuiyuan.user.domain.Role;
import com.sanshuiyuan.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B.1.3: UserRepository CRUD against real MySQL (Testcontainers + Flyway).
 *
 * Defect #3 is now fixed, so this slice loads the full repository layer with no exclusions
 * (UserRoleRepository's derived queries are covered in {@link UserRoleRepositoryIT}).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoriesIT extends AbstractMysqlContainerTest {

    @Autowired
    UserRepository userRepository;

    private User newUser(String unionid) {
        User u = new User();
        u.setUnionid(unionid);
        u.setOpenidWx("wx-" + unionid);
        u.setOpenidApp("app-" + unionid);
        u.setNickname("张三");
        u.setActiveRole(Role.CONSUMER);
        return u;
    }

    @Test
    void user_saveAndFindByUnionid() {
        User saved = userRepository.save(newUser("union-1"));
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        assertThat(userRepository.findByUnionid("union-1")).isPresent();
        assertThat(userRepository.findByOpenidWx("wx-union-1")).isPresent();
        assertThat(userRepository.findByOpenidApp("app-union-1")).isPresent();
        assertThat(userRepository.findByUnionid("missing")).isEmpty();
    }

    @Test
    void user_updateActiveRole_persists() {
        User saved = userRepository.save(newUser("union-2"));
        saved.setActiveRole(Role.OWNER);
        userRepository.saveAndFlush(saved);

        User reloaded = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getActiveRole()).isEqualTo(Role.OWNER);
    }
}
