package com.sanshuiyuan.matching.request.application;

import com.sanshuiyuan.matching.request.api.dto.ActivateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 029 设备激活端到端集成测试（真 MySQL + 真 Flyway + 真 CAS）。
 *
 * <p>device_assets 归 admin 域、matching 仅 native SQL 读写，故测试夹具建表（含 sn 列，activateBySn 需要）。
 * @Tag("integration") 仅 CI/本地 IT 跑（root build.gradle 把 *IT 从 test 任务排除）。
 */
@Tag("integration")
@SpringBootTest
class ActivateDeviceUseCaseIT {

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
    ActivateDeviceUseCase activateDeviceUseCase;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void resetFixtures() {
        jdbc.execute("DROP TABLE IF EXISTS device_assets");
        jdbc.execute("CREATE TABLE device_assets (" +
                "id BIGINT PRIMARY KEY, user_id BIGINT NOT NULL, sn VARCHAR(64) NOT NULL UNIQUE, " +
                "stage VARCHAR(16) NOT NULL, KEY idx_user (user_id))");
        jdbc.update("DELETE FROM device_assets_stage_events");
    }

    private void insertDevice(long id, String sn, String stage) {
        jdbc.update("INSERT INTO device_assets (id, user_id, sn, stage) VALUES (?, 1001, ?, ?)", id, sn, stage);
    }

    private String stageOf(String sn) {
        return jdbc.queryForObject("SELECT stage FROM device_assets WHERE sn = ?", String.class, sn);
    }

    private long stageEventCount(long deviceAssetId, String type) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM device_assets_stage_events WHERE device_asset_id = ? AND event_type = ?",
                Long.class, deviceAssetId, type);
    }

    @Test
    void pendingActivate_advancesToStage1_andWritesEvent() {
        insertDevice(2001L, "SN-ACT-001", "PENDING_ACTIVATE");

        ActivateResponse resp = activateDeviceUseCase.activate("SN-ACT-001");

        assertThat(resp.activated()).isTrue();
        assertThat(stageOf("SN-ACT-001")).isEqualTo("STAGE_1");
        assertThat(stageEventCount(2001L, "STAGE_1_ACTIVATED")).isEqualTo(1L);
    }

    @Test
    void repeatedActivate_isIdempotent_noSecondEvent() {
        insertDevice(2002L, "SN-ACT-002", "PENDING_ACTIVATE");

        assertThat(activateDeviceUseCase.activate("SN-ACT-002").activated()).isTrue();
        // 第二次（设备已 STAGE_1）：CAS 命中 0 行 → no-op，不再写事件。
        assertThat(activateDeviceUseCase.activate("SN-ACT-002").activated()).isFalse();

        assertThat(stageOf("SN-ACT-002")).isEqualTo("STAGE_1");
        assertThat(stageEventCount(2002L, "STAGE_1_ACTIVATED")).isEqualTo(1L);
    }

    @Test
    void notPendingActivate_isNoop() {
        insertDevice(2003L, "SN-ACT-003", "LOCKED");          // 未履约
        insertDevice(2004L, "SN-ACT-004", "STAGE_1");          // 已激活

        assertThat(activateDeviceUseCase.activate("SN-ACT-003").activated()).isFalse();
        assertThat(activateDeviceUseCase.activate("SN-ACT-004").activated()).isFalse();
        assertThat(activateDeviceUseCase.activate("SN-NONEXISTENT").activated()).isFalse();

        assertThat(stageOf("SN-ACT-003")).isEqualTo("LOCKED");
        assertThat(stageEventCount(2003L, "STAGE_1_ACTIVATED")).isEqualTo(0L);
    }
}
