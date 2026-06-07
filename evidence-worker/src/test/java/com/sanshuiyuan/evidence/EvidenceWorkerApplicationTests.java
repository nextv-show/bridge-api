package com.sanshuiyuan.evidence;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * 上下文冒烟测试：用 Testcontainers 起真实 MySQL 提供可连数据源。
 * evidence-worker 共享 water_db、其表由 water-service 维护，自身迁移仅占位 baseline，
 * 故测试态关闭 Flyway 且 ddl-auto=none（实体映射的表不在本服务建表范围内）。
 */
@SpringBootTest
class EvidenceWorkerApplicationTests {

    @SuppressWarnings("resource")
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("water_db")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        if (!MYSQL.isRunning()) {
            MYSQL.start();
        }
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
        r.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        r.add("spring.flyway.enabled", () -> "false");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @Test
    void contextLoads() {
    }
}
