package com.sanshuiyuan.h5;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * 单例 MySQL 容器（withReuse），Flyway 跑真实迁移脚本（V000~V004）建表 + seed。
 * Redis 在测试中不可用，由 LandingConfigService 的 try/catch 优雅降级（不影响断言）。
 */
public abstract class AbstractMysqlContainerTest {

    @SuppressWarnings("resource")
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("h5_db")
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
        // 测试中 L2 Redis 视为不可用：指向一个关闭的端口，readRedis/writeRedis 快速失败并优雅降级，
        // 让缓存命中断言只反映 L1(Caffeine) 行为，并避免污染本机其它项目占用的 6379 Redis。
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> "6399");
        registry.add("spring.data.redis.timeout", () -> "150ms");
    }
}
