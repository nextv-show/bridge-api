package com.sanshuiyuan.matching.request.application;

import com.sanshuiyuan.matching.request.api.ApiException;
import com.sanshuiyuan.matching.request.api.dto.ConfirmResponse;
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
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * P1-2 确认推进端到端集成测试（真 MySQL + 真 Flyway + 真事务/乐观锁）。
 *
 * <p>确认成功后写 logistics_outbox 触发发货（接单时不写，避免未确认即建/撤物流工单的空转）。
 * 与 {@link ClaimRequestUseCaseIT} 同模式：device_assets / users 非托管表用夹具建表复现约束，
 * matching_* 三表由 Flyway 真迁移建。@Tag("integration") 仅 CI 跑。
 */
@Tag("integration")
@SpringBootTest
class ConfirmClaimUseCaseIT {

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
    ConfirmClaimUseCase confirmClaimUseCase;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void resetFixtures() {
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

    /** 写入处于指定 status、由 lockerUserId 锁定的需求（locked_at=now，claim_confirmed_at=null）。 */
    private void insertLockedRequest(long id, long posterUserId, String status, Long lockerUserId) {
        jdbc.update("INSERT INTO matching_requests " +
                        "(id, user_id, contact_name, contact_phone_enc, contact_phone_hash, address, lat, lng, " +
                        " geohash6, scene_type, est_daily_liters, expected_price_tier, status, " +
                        " locked_by_user_id, locked_at, version) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)",
                id, posterUserId, "联系人" + id, new byte[]{1, 2, 3}, "phash_" + id,
                "广州市天河区xx路1号", new BigDecimal("23.1300000"), new BigDecimal("113.2600000"),
                "ws10s0", "HOME", 100, "T_080", status, lockerUserId, LocalDateTime.now());
    }

    private void insertActiveAssignment(long requestId, long deviceId, long ownerId) {
        jdbc.update("INSERT INTO matching_assignments (request_id, device_asset_id, owner_user_id) VALUES (?, ?, ?)",
                requestId, deviceId, ownerId);
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0L : n;
    }

    // ——————————————————————————————————————————————————————————————————————
    //  场景
    // ——————————————————————————————————————————————————————————————————————

    @Test
    void confirm_success_writesLogisticsOutbox() {
        long owner = 10L, device = 3001L, request = 9001L, poster = 99L;
        insertUser(owner, "oCONFIRM_ok");
        insertLockedRequest(request, poster, "LOCKED", owner);
        insertActiveAssignment(request, device, owner);

        confirmClaimUseCase.confirm("oCONFIRM_ok", request);

        Long outboxId = jdbc.queryForObject(
                "SELECT id FROM logistics_outbox WHERE request_id = ? AND device_asset_id = ?",
                Long.class, request, device);
        assertThat(outboxId).isNotNull();
        String payload = jdbc.queryForObject(
                "SELECT payload_json FROM logistics_outbox WHERE id = ?", String.class, outboxId);
        assertThat(payload).contains("ship_to_address").contains("广州市天河区xx路1号");
        String source = jdbc.queryForObject(
                "SELECT source FROM logistics_outbox WHERE id = ?", String.class, outboxId);
        assertThat(source).isEqualTo("MATCHING");
    }

    @Test
    void confirm_success_setsPendingLogistics() {
        long owner = 20L, device = 3101L, request = 9101L, poster = 99L;
        insertUser(owner, "oCONFIRM_pending");
        insertLockedRequest(request, poster, "LOCKED", owner);
        insertActiveAssignment(request, device, owner);

        ConfirmResponse resp = confirmClaimUseCase.confirm("oCONFIRM_pending", request);

        assertThat(resp.status()).isEqualTo("LOCKED");
        assertThat(resp.claimConfirmedAt()).isNotNull();
        assertThat(resp.pendingLogistics()).isTrue();
    }

    @Test
    void confirm_notLockedByCaller_throws403() {
        long owner = 30L, other = 31L, device = 3201L, request = 9201L, poster = 99L;
        insertUser(owner, "oCONFIRM_owner");
        insertUser(other, "oCONFIRM_other");
        insertLockedRequest(request, poster, "LOCKED", owner);   // 锁定方为 owner
        insertActiveAssignment(request, device, owner);

        ApiException e = catchThrowableOfType(
                () -> confirmClaimUseCase.confirm("oCONFIRM_other", request), ApiException.class);

        assertThat(e).isNotNull();
        assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(e.getCode()).isEqualTo("NOT_LOCK_OWNER");
        assertThat(count("SELECT COUNT(*) FROM logistics_outbox WHERE request_id = ?", request)).isZero();
    }

    @Test
    void confirm_notLocked_throws409() {
        // 锁定方为 owner，但需求已 FULFILLED（非 LOCKED）→ 通过 403 校验后撞 409。
        long owner = 40L, device = 3301L, request = 9301L, poster = 99L;
        insertUser(owner, "oCONFIRM_notlocked");
        insertLockedRequest(request, poster, "FULFILLED", owner);
        insertActiveAssignment(request, device, owner);

        ApiException e = catchThrowableOfType(
                () -> confirmClaimUseCase.confirm("oCONFIRM_notlocked", request), ApiException.class);

        assertThat(e).isNotNull();
        assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(e.getCode()).isEqualTo("REQUEST_NOT_LOCKED");
        assertThat(count("SELECT COUNT(*) FROM logistics_outbox WHERE request_id = ?", request)).isZero();
    }

    @Test
    void confirm_idempotent_returnsSameTimestamp() {
        long owner = 50L, device = 3401L, request = 9401L, poster = 99L;
        insertUser(owner, "oCONFIRM_idem");
        insertLockedRequest(request, poster, "LOCKED", owner);
        insertActiveAssignment(request, device, owner);

        ConfirmResponse first = confirmClaimUseCase.confirm("oCONFIRM_idem", request);
        ConfirmResponse second = confirmClaimUseCase.confirm("oCONFIRM_idem", request);

        // 幂等：时间戳一致，且不重复写 outbox（仅一条）。
        assertThat(second.claimConfirmedAt()).isEqualTo(first.claimConfirmedAt());
        assertThat(count("SELECT COUNT(*) FROM logistics_outbox WHERE request_id = ?", request)).isEqualTo(1L);
    }
}
