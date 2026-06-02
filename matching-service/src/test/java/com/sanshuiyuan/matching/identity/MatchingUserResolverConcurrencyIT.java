package com.sanshuiyuan.matching.identity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * #19 回归：并发首触同一 openid 的 {@link MatchingUserResolver#resolveUserId} 幂等性。
 *
 * <p>验证 admin V080 的 {@code users.openid} 唯一键（uk_openid）+ resolver 的
 * {@code INSERT ... ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id)} 组合：
 * N 个线程并发解析同一新 openid，结果必须是「{@code users} 只产一行」且「所有线程返回同一 id」，
 * 而非裸 SELECT-then-INSERT 的重复行竞态。
 *
 * <p>说明：{@code users}/{@code h5_users} 由 admin/h5 拥有，matching 不迁移它们（仅 JdbcTemplate 读写），
 * 故此处用测试夹具建表（含 uk_openid）以复现真实约束。Testcontainers，@Tag("integration") 仅 CI 跑。
 */
@Tag("integration")
@SpringBootTest
class MatchingUserResolverConcurrencyIT {

    @SuppressWarnings("resource")
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("h5_db").withUsername("test").withPassword("test").withReuse(true);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        if (!MYSQL.isRunning()) MYSQL.start();
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MatchingUserResolver resolver;

    @BeforeEach
    void setupUserTables() {
        // 重建 users（含 uk_openid，对齐 admin V074+V080）与 h5_users（resolver 读昵称/头像）。
        jdbc.execute("DROP TABLE IF EXISTS users");
        jdbc.execute("CREATE TABLE users (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, openid VARCHAR(64), nickname VARCHAR(64), avatar_url VARCHAR(512), " +
                "channel VARCHAR(16) NOT NULL DEFAULT 'WECHAT_MP', tier VARCHAR(16) NOT NULL DEFAULT 'NORMAL', " +
                "tags VARCHAR(256) NOT NULL DEFAULT '', status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', " +
                "kyc_status VARCHAR(16) NOT NULL DEFAULT 'NONE', " +
                "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE KEY uk_openid (openid))");
        jdbc.execute("DROP TABLE IF EXISTS h5_users");
        jdbc.execute("CREATE TABLE h5_users (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, openid VARCHAR(64), nickname VARCHAR(64), avatar_url VARCHAR(512))");
    }

    @Test
    void concurrentResolveSameOpenid_yieldsSingleRowAndSameId() throws Exception {
        String openid = "oTEST_concurrency_19";
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<Long>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    startGate.await();                 // 同时起跑，最大化竞争
                    return resolver.resolveUserId(openid);
                }));
            }
            startGate.countDown();

            Set<Long> ids = new HashSet<>();
            for (Future<Long> f : futures) {
                ids.add(f.get(15, TimeUnit.SECONDS));
            }

            Long rowCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE openid = ?", Long.class, openid);
            assertEquals(1L, rowCount, "并发首触应只产生一行 users");
            assertEquals(1, ids.size(), "所有线程必须返回同一 user_id，实际=" + ids);
        } finally {
            pool.shutdownNow();
        }
    }
}
