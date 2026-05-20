package com.sanshuiyuan.user;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * Shared MySQL Testcontainers base for user-service persistence tests. Flyway runs the real
 * db/migration scripts so derived queries are exercised against the production schema
 * (ENUM columns, composite user_roles PK, FKs).
 *
 * Uses the singleton-container pattern: ONE MySQL container is started for the whole test JVM and
 * reused by every test class (started once, never stopped — Ryuk/JVM shutdown reaps it). This
 * avoids spinning up one container per class, which under Docker resource pressure (and with Ryuk
 * disabled) could leave a class pointing at a container that never became reachable.
 */
public abstract class AbstractMysqlContainerTest {

    @SuppressWarnings("resource")
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("user_db")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}
