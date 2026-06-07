package com.sanshuiyuan.matching.request.application;

import com.sanshuiyuan.matching.request.api.ApiException;
import com.sanshuiyuan.matching.request.api.dto.ClaimRequestResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * FR-3 接单原子事务端到端集成测试（真 MySQL + 真 Flyway V010–V014 + 真事务/乐观锁/唯一键）。
 *
 * <p>device_assets / users / h5_users 归 asset/admin/h5 域，matching 不迁移它们（仅 JdbcTemplate 读写），
 * 故此处用测试夹具建表复现真实约束；matching_* 三表由 Flyway 真迁移建。@Tag("integration") 仅 CI 跑。
 */
@Tag("integration")
@SpringBootTest
class ClaimRequestUseCaseIT {

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

    @Autowired
    ClaimRequestUseCase claimRequestUseCase;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void resetFixtures() {
        // 非托管表（asset/admin/h5 域）：重建以复现归属与 stage 约束。device_assets.stage 用 VARCHAR
        // 而非 asset 域的 ENUM —— 后者尚未含 LOCKED（DeviceStage javadoc C.2 阻塞点），测试需写入 LOCKED。
        jdbc.execute("DROP TABLE IF EXISTS device_assets");
        jdbc.execute("CREATE TABLE device_assets (" +
                "id BIGINT PRIMARY KEY, user_id BIGINT NOT NULL, stage VARCHAR(16) NOT NULL, KEY idx_user (user_id))");
        jdbc.execute("DROP TABLE IF EXISTS users");
        jdbc.execute("CREATE TABLE users (" +
                "id BIGINT PRIMARY KEY, openid VARCHAR(64), nickname VARCHAR(64), avatar_url VARCHAR(512), " +
                "channel VARCHAR(16) NOT NULL DEFAULT 'WECHAT_MP', tier VARCHAR(16) NOT NULL DEFAULT 'NORMAL', " +
                "tags VARCHAR(256) NOT NULL DEFAULT '', status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', " +
                "kyc_status VARCHAR(16) NOT NULL DEFAULT 'NONE', " +
                "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE KEY uk_openid (openid))");
        jdbc.execute("DROP TABLE IF EXISTS h5_users");
        jdbc.execute("CREATE TABLE h5_users (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, openid VARCHAR(64), nickname VARCHAR(64), avatar_url VARCHAR(512))");

        // 托管表（matching 域，Flyway 已建）：清空保证用例间隔离。
        jdbc.update("DELETE FROM logistics_outbox");
        jdbc.update("DELETE FROM matching_assignments");
        jdbc.update("DELETE FROM matching_requests");
    }

    // ——————————————————————————————————————————————————————————————————————
    //  夹具
    // ——————————————————————————————————————————————————————————————————————

    private void insertUser(long id, String openid) {
        jdbc.update("INSERT INTO users (id, openid, nickname) VALUES (?, ?, ?)", id, openid, "用户" + id);
    }

    private void insertDevice(long id, long userId, String stage) {
        jdbc.update("INSERT INTO device_assets (id, user_id, stage) VALUES (?, ?, ?)", id, userId, stage);
    }

    private void insertOpenRequest(long id, long posterUserId) {
        jdbc.update("INSERT INTO matching_requests " +
                        "(id, user_id, contact_name, contact_phone_enc, contact_phone_hash, address, lat, lng, " +
                        " geohash6, scene_type, est_daily_liters, expected_price_tier, status, version) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', 0)",
                id, posterUserId, "联系人" + id, new byte[]{1, 2, 3}, "phash_" + id,
                "广州市天河区xx路1号", new BigDecimal("23.1300000"), new BigDecimal("113.2600000"),
                "ws10s0", "HOME", 100, "T_080");
    }

    private void insertActiveAssignment(long requestId, long deviceId, long ownerId) {
        jdbc.update("INSERT INTO matching_assignments (request_id, device_asset_id, owner_user_id) VALUES (?, ?, ?)",
                requestId, deviceId, ownerId);
    }

    private String stageOf(long deviceId) {
        return jdbc.queryForObject("SELECT stage FROM device_assets WHERE id = ?", String.class, deviceId);
    }

    private String statusOf(long requestId) {
        return jdbc.queryForObject("SELECT status FROM matching_requests WHERE id = ?", String.class, requestId);
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0L : n;
    }

    // ——————————————————————————————————————————————————————————————————————
    //  场景
    // ——————————————————————————————————————————————————————————————————————

    @Test
    void claim_success_locksRequestAndDevice_writesAssignmentAndOutbox() {
        long owner = 10L, device = 2001L, request = 7001L, poster = 99L;
        insertUser(owner, "oCLAIM_ok");
        insertDevice(device, owner, "PENDING_MATCH");
        insertOpenRequest(request, poster);

        ClaimRequestResponse resp = claimRequestUseCase.claim("oCLAIM_ok", request, device);

        assertThat(resp.status()).isEqualTo("LOCKED");
        assertThat(resp.pendingLogistics()).isTrue();
        assertThat(statusOf(request)).isEqualTo("LOCKED");
        assertThat(stageOf(device)).isEqualTo("LOCKED");

        assertThat(count("SELECT COUNT(*) FROM matching_assignments WHERE request_id = ? AND device_asset_id = ? " +
                "AND owner_user_id = ? AND released_at IS NULL", request, device, owner)).isEqualTo(1L);
        assertThat(count("SELECT COUNT(*) FROM matching_requests WHERE id = ? AND locked_by_user_id = ?",
                request, owner)).isEqualTo(1L);

        Long outboxId = jdbc.queryForObject(
                "SELECT id FROM logistics_outbox WHERE request_id = ? AND device_asset_id = ?",
                Long.class, request, device);
        assertThat(outboxId).isNotNull();
        String payload = jdbc.queryForObject(
                "SELECT payload_json FROM logistics_outbox WHERE id = ?", String.class, outboxId);
        assertThat(payload).contains("ship_to_address").contains("广州市天河区xx路1号");
    }

    @Test
    void claim_concurrent_sameRequestDistinctDevices_onlyOneWins_otherGets409_noDirtyState() throws Exception {
        long owner = 20L, deviceA = 2101L, deviceB = 2102L, request = 7101L, poster = 99L;
        insertUser(owner, "oCLAIM_race");
        insertDevice(deviceA, owner, "PENDING_MATCH");
        insertDevice(deviceB, owner, "PENDING_MATCH");
        insertOpenRequest(request, poster);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (long device : new long[]{deviceA, deviceB}) {
                final long dev = device;
                tasks.add(() -> {
                    start.await();
                    try {
                        claimRequestUseCase.claim("oCLAIM_race", request, dev);
                        success.incrementAndGet();
                    } catch (ApiException e) {
                        assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                        conflict.incrementAndGet();
                    }
                    return null;
                });
            }
            List<Future<Void>> futures = new ArrayList<>();
            for (Callable<Void> t : tasks) {
                futures.add(pool.submit(t));
            }
            start.countDown();
            for (Future<Void> f : futures) {
                f.get(20, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        // 恰一胜一负。
        assertThat(success.get()).isEqualTo(1);
        assertThat(conflict.get()).isEqualTo(1);

        // 原子性：需求 LOCKED；仅一条活跃占用；仅一条 outbox；恰一台设备 LOCKED，另一台回滚为 PENDING_MATCH（无脏写）。
        assertThat(statusOf(request)).isEqualTo("LOCKED");
        assertThat(count("SELECT COUNT(*) FROM matching_assignments WHERE request_id = ? AND released_at IS NULL",
                request)).isEqualTo(1L);
        assertThat(count("SELECT COUNT(*) FROM logistics_outbox WHERE request_id = ?", request)).isEqualTo(1L);
        assertThat(count("SELECT COUNT(*) FROM device_assets WHERE id IN (?, ?) AND stage = 'LOCKED'",
                deviceA, deviceB)).isEqualTo(1L);
        assertThat(count("SELECT COUNT(*) FROM device_assets WHERE id IN (?, ?) AND stage = 'PENDING_MATCH'",
                deviceA, deviceB)).isEqualTo(1L);
    }

    @Test
    void claim_deviceNotOwnedByCaller_throws403() {
        long caller = 30L, otherOwner = 31L, device = 2201L, request = 7201L;
        insertUser(caller, "oCLAIM_notowner");
        insertDevice(device, otherOwner, "PENDING_MATCH");   // 设备属于他人
        insertOpenRequest(request, 99L);

        ApiException e = catchThrowableOfType(
                () -> claimRequestUseCase.claim("oCLAIM_notowner", request, device), ApiException.class);

        assertThat(e).isNotNull();
        assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(e.getCode()).isEqualTo("NOT_OWNER_ASSET");
        assertThat(statusOf(request)).isEqualTo("OPEN");
        assertThat(count("SELECT COUNT(*) FROM logistics_outbox WHERE request_id = ?", request)).isZero();
    }

    @Test
    void claim_deviceStageNotPendingMatch_throws409() {
        long owner = 40L, device = 2301L, request = 7301L;
        insertUser(owner, "oCLAIM_stage");
        insertDevice(device, owner, "STAGE_1");   // 非 PENDING_MATCH，不可接单
        insertOpenRequest(request, 99L);

        ApiException e = catchThrowableOfType(
                () -> claimRequestUseCase.claim("oCLAIM_stage", request, device), ApiException.class);

        assertThat(e).isNotNull();
        assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(e.getCode()).isEqualTo("DEVICE_STAGE_INVALID");
        assertThat(stageOf(device)).isEqualTo("STAGE_1");
        assertThat(statusOf(request)).isEqualTo("OPEN");
        assertThat(count("SELECT COUNT(*) FROM logistics_outbox WHERE request_id = ?", request)).isZero();
    }

    @Test
    void claim_ownerAtLockLimit_throws409() {
        long owner = 50L, device = 2401L, request = 7401L;
        insertUser(owner, "oCLAIM_limit");
        insertDevice(device, owner, "PENDING_MATCH");
        insertOpenRequest(request, 99L);
        // 预置 lock.max.per.owner（默认 5）条活跃占用，使本次接单触顶。
        for (int i = 0; i < 5; i++) {
            insertActiveAssignment(8000L + i, 9000L + i, owner);
        }

        ApiException e = catchThrowableOfType(
                () -> claimRequestUseCase.claim("oCLAIM_limit", request, device), ApiException.class);

        assertThat(e).isNotNull();
        assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(e.getCode()).isEqualTo("LOCK_LIMIT");
        assertThat(stageOf(device)).isEqualTo("PENDING_MATCH");
        assertThat(statusOf(request)).isEqualTo("OPEN");
        assertThat(count("SELECT COUNT(*) FROM logistics_outbox WHERE request_id = ?", request)).isZero();
    }

    @Test
    void claim_assignmentInsertViolatesUnique_rollsBackEntirely_noDirtyOutbox() {
        long owner = 60L, device = 2501L, request = 7501L, otherRequest = 8888L;
        insertUser(owner, "oCLAIM_rollback");
        insertDevice(device, owner, "PENDING_MATCH");
        insertOpenRequest(request, 99L);
        // 同一设备已有活跃占用（绑定另一需求）→ 本次接单写 assignment 必触 uk_device_active。
        // 设备仍保持 PENDING_MATCH，使前面的设备 CAS 成功推进，从而真正走到 assignment 落库失败 → 整事务回滚。
        insertActiveAssignment(otherRequest, device, owner);

        assertThatThrownBy(() -> claimRequestUseCase.claim("oCLAIM_rollback", request, device))
                .isInstanceOf(ApiException.class)
                .satisfies(t -> assertThat(((ApiException) t).getStatus()).isEqualTo(HttpStatus.CONFLICT));

        // 整事务回滚：设备 stage 复位、需求仍 OPEN、未新增占用、无脏 outbox。
        assertThat(stageOf(device)).isEqualTo("PENDING_MATCH");
        assertThat(statusOf(request)).isEqualTo("OPEN");
        assertThat(count("SELECT COUNT(*) FROM matching_assignments WHERE request_id = ?", request)).isZero();
        assertThat(count("SELECT COUNT(*) FROM matching_assignments WHERE device_asset_id = ? AND released_at IS NULL",
                device)).isEqualTo(1L);   // 仅预置的那条仍在
        assertThat(count("SELECT COUNT(*) FROM logistics_outbox")).isZero();
    }
}
