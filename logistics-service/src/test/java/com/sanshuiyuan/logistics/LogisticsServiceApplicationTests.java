package com.sanshuiyuan.logistics;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/** Phase A 验收：启动上下文 + Flyway 应用 V010-V011（logistics_* 建表）。 */
@Tag("integration")
@SpringBootTest
class LogisticsServiceApplicationTests {

    @SuppressWarnings("resource")
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("core_db").withUsername("test").withPassword("test").withReuse(true);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        if (!MYSQL.isRunning()) MYSQL.start();
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Test
    void contextLoads() {
    }
}
