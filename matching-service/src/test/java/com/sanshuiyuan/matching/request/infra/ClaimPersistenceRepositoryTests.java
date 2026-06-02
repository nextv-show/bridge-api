package com.sanshuiyuan.matching.request.infra;

import com.sanshuiyuan.matching.assignment.domain.MatchingAssignment;
import com.sanshuiyuan.matching.assignment.infra.MatchingAssignmentRepository;
import com.sanshuiyuan.matching.logistics.domain.LogisticsOutboxEntry;
import com.sanshuiyuan.matching.logistics.infra.LogisticsOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * C.1.2 仓储 + active-unique 唯一键（V014）回归。Testcontainers 真 MySQL + 真 Flyway（V010–V014），
 * 同时验证实体↔schema 映射（ddl-auto=validate）。@Tag("integration") 仅 CI 跑。
 */
@Tag("integration")
@SpringBootTest
class ClaimPersistenceRepositoryTests {

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
    MatchingAssignmentRepository assignments;

    @Autowired
    LogisticsOutboxRepository outbox;

    @BeforeEach
    void clean() {   // 容器在方法间复用，逐方法清表保证隔离（计数/唯一键断言不被他法残留干扰）。
        outbox.deleteAll();
        assignments.deleteAll();
    }

    private static MatchingAssignment assignment(long requestId, long deviceAssetId, long ownerUserId) {
        MatchingAssignment a = new MatchingAssignment();
        a.setRequestId(requestId);
        a.setDeviceAssetId(deviceAssetId);
        a.setOwnerUserId(ownerUserId);
        return a;
    }

    @Test
    void persistsAssignment_andDbFillsLockedAt() {
        MatchingAssignment saved = assignments.saveAndFlush(assignment(1001L, 2001L, 5L));
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getLockedAt()).isNotNull();   // DB 默认 CURRENT_TIMESTAMP 写入

        assertThat(assignments.findByRequestIdAndReleasedAtIsNull(1001L)).isPresent();
        assertThat(assignments.findByDeviceAssetIdAndReleasedAtIsNull(2001L)).isPresent();
        assertThat(assignments.countByOwnerUserIdAndReleasedAtIsNull(5L)).isEqualTo(1L);
    }

    @Test
    void ukRequest_blocksSecondActiveAssignmentForSameRequest() {
        assignments.saveAndFlush(assignment(1002L, 2002L, 5L));
        // 同一活跃需求再占用（不同设备）→ uk_request(active_request_lock) 命中。
        assertThatThrownBy(() -> assignments.saveAndFlush(assignment(1002L, 2099L, 6L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void ukDeviceActive_blocksSecondActiveAssignmentForSameDevice() {
        assignments.saveAndFlush(assignment(1003L, 2003L, 5L));
        // 同一活跃设备再占用（不同需求）→ uk_device_active(active_device_lock) 命中。
        assertThatThrownBy(() -> assignments.saveAndFlush(assignment(1099L, 2003L, 6L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void release_thenReclaim_sameRequestAndDevice_isAllowed() {
        MatchingAssignment first = assignments.saveAndFlush(assignment(1004L, 2004L, 5L));
        // 释放：active 生成列变 NULL，退出唯一约束（FR-5.3）。
        first.setReleasedAt(LocalDateTime.now());
        assignments.saveAndFlush(first);

        // 同需求 + 同设备重新接单，应成功。
        MatchingAssignment reclaim = assignments.saveAndFlush(assignment(1004L, 2004L, 6L));
        assertThat(reclaim.getId()).isNotNull();
        assertThat(assignments.findByRequestIdAndReleasedAtIsNull(1004L))
                .get().extracting(MatchingAssignment::getOwnerUserId).isEqualTo(6L);
    }

    @Test
    void outbox_persistsJsonAndPendingQueryReturnsUnconsumed() {
        LogisticsOutboxEntry e = new LogisticsOutboxEntry();
        e.setRequestId(1005L);
        e.setDeviceAssetId(2005L);
        e.setPayloadJson("{\"ship_to_address\":\"广州市天河区xx路1号\",\"request_id\":1005}");
        LogisticsOutboxEntry saved = outbox.saveAndFlush(e);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();

        List<LogisticsOutboxEntry> pending = outbox.findByConsumedAtIsNullOrderByCreatedAtAsc();
        assertThat(pending).extracting(LogisticsOutboxEntry::getId).contains(saved.getId());
        assertThat(pending).filteredOn(o -> o.getId().equals(saved.getId()))
                .singleElement()
                .extracting(LogisticsOutboxEntry::getPayloadJson).asString()
                .contains("ship_to_address");
    }
}
