package com.sanshuiyuan.water;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * 上下文冒烟测试：用 Testcontainers 起真实 MySQL 跑 Flyway 迁移（V030~V039 建表），
 * JPA ddl-auto=validate 校验实体与表一致。Redis 指向关闭端口（测试不依赖）。
 */
@SpringBootTest
class WaterServiceApplicationTests {

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
        r.add("spring.flyway.enabled", () -> "true");
        r.add("spring.flyway.locations", () -> "classpath:db/migration");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("spring.data.redis.host", () -> "127.0.0.1");
        r.add("spring.data.redis.port", () -> "6399");
        r.add("spring.data.redis.timeout", () -> "150ms");
    }

    @Test
    void contextLoads() {
    }
}
