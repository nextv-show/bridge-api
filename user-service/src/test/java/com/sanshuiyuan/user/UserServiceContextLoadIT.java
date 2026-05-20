package com.sanshuiyuan.user;

import com.sanshuiyuan.user.infra.repository.HomeLayoutPrefRepository;
import com.sanshuiyuan.user.infra.repository.UserRepository;
import com.sanshuiyuan.user.infra.repository.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B.1.3 (fix verification, final proof of defect #3): boots the entire user-service Spring
 * context against a real MySQL (Testcontainers + Flyway).
 *
 * This is precisely the path that used to fail at startup: while UserRoleRepository's derived
 * queries omitted the embedded-id traversal, the JPA repositories could not be created and the
 * context refused to start ("No property 'userId' found for type 'UserRole'"). A successful
 * context load here, with every repository bean present and injectable, proves the fix end to end.
 */
@SpringBootTest
class UserServiceContextLoadIT extends AbstractMysqlContainerTest {

    @Autowired
    ApplicationContext context;

    @Autowired
    UserRepository userRepository;

    @Autowired
    UserRoleRepository userRoleRepository;

    @Autowired
    HomeLayoutPrefRepository homeLayoutPrefRepository;

    @Test
    void contextLoads_withAllRepositoryBeans() {
        // Context started; the repository that triggered defect #3 is now a real, injected bean.
        assertThat(context).isNotNull();
        assertThat(userRepository).isNotNull();
        assertThat(userRoleRepository).isNotNull();
        assertThat(homeLayoutPrefRepository).isNotNull();

        // All JPA repository beans must be present (defect #3 prevented them from being created).
        assertThat(context.getBeanNamesForType(
                org.springframework.data.repository.Repository.class)).isNotEmpty();
        assertThat(context.containsBean("userRoleRepository")).isTrue();
    }
}
