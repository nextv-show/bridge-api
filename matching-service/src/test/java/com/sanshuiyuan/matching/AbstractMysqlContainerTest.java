package com.sanshuiyuan.matching;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Shared MySQL 8 test container for matching-service persistence tests. */
@Testcontainers
@Tag("integration")
@DisabledIfEnvironmentVariable(named = "CI_SKIP_IT", matches = "true")
public abstract class AbstractMysqlContainerTest {

    @Container
    @SuppressWarnings("resource")
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("core_db")
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
