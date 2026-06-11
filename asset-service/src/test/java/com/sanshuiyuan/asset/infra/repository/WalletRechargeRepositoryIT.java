package com.sanshuiyuan.asset.infra.repository;

import com.sanshuiyuan.asset.AbstractMysqlContainerTest;
import com.sanshuiyuan.asset.domain.WalletRecharge;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 findHistoryByUserId 的 @Query 排序：
 * 按 COALESCE(paidAt, createdAt) DESC 的「事件时间」降序，同一时刻以 id DESC 作确定性 tiebreaker。
 * 同时验证 JPQL 能对真实实体/Flyway schema 解析通过（生产 ddl-auto=validate）。
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class WalletRechargeRepositoryIT extends AbstractMysqlContainerTest {

    @Autowired
    WalletRechargeRepository repo;

    private static void set(Object t, String field, Object v) {
        try {
            Field f = WalletRecharge.class.getDeclaredField(field);
            f.setAccessible(true);
            f.set(t, v);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private WalletRecharge save(long userId, LocalDateTime createdAt, LocalDateTime paidAt) {
        WalletRecharge r = WalletRecharge.create(userId, 10000L, 0, 0, "WECHAT");
        set(r, "createdAt", createdAt);
        set(r, "paidAt", paidAt);
        return repo.saveAndFlush(r);
    }

    @Test
    void ordersByEventTimeThenIdAndIsolatesByUser() {
        long u = 9001L;
        LocalDateTime base = LocalDateTime.of(2026, 6, 1, 10, 0, 0);

        // A: 早创建、晚入账（pending→paid 的长尾）——应按 paidAt 排到最前
        WalletRecharge a = save(u, base, base.plusHours(5));
        // B: 晚创建、未支付——按 createdAt 排
        WalletRecharge b = save(u, base.plusHours(2), null);
        // C/D: 同一创建时刻且都未支付——以 id DESC 决定先后（确定性 tiebreaker）
        WalletRecharge c = save(u, base.plusHours(1), null);
        WalletRecharge d = save(u, base.plusHours(1), null);
        // 他人订单——必须被隔离
        save(8888L, base.plusHours(9), base.plusHours(9));

        Page<WalletRecharge> page = repo.findHistoryByUserId(u, PageRequest.of(0, 20));
        List<Long> ids = page.getContent().stream().map(WalletRecharge::getId).toList();

        // 只含本人 4 条
        assertThat(page.getTotalElements()).isEqualTo(4);
        // 事件时间降序：A(15:00) > B(12:00) > C/D(11:00)；C/D 同刻按 id DESC
        long firstTie = Math.max(c.getId(), d.getId());
        long secondTie = Math.min(c.getId(), d.getId());
        assertThat(ids).containsExactly(a.getId(), b.getId(), firstTie, secondTie);
    }
}
