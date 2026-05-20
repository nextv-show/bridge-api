package com.sanshuiyuan.asset;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Shared MySQL Testcontainers base for asset-service persistence tests. A single MySQL 8
 * container is reused across subclasses (static field), with Flyway running the real
 * db/migration scripts (ENUM / JSON columns) so tests exercise the production schema.
 *
 * Note: kept as a per-class @Container (NOT the singleton pattern used by user-service) on
 * purpose — PayCallbackUseCaseIT is a non-transactional @SpringBootTest that deletes/commits
 * (including the V004-seeded SKUs), so each persistence class needs its own freshly-seeded
 * container to stay isolated from that teardown.
 */
@Testcontainers
public abstract class AbstractMysqlContainerTest {

    @Container
    @SuppressWarnings("resource")
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("asset_db")
            .withUsername("test")
            .withPassword("test");

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
