package com.sanshuiyuan.asset.rebate.infra.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L3+ 物理隔离守卫（合规铁律）。
 *
 * <p>以反射断言：返利台账仓储 {@link PendingRebateRepository} <b>不得</b>提供任何能形成
 * 「邀请人的邀请人的…」三级以上链路追溯的查询：
 * <ul>
 *   <li>派生查询方法名不得以 {@code grand_inviter}/{@code inviter} 关系链字段为查询条件
 *       （不含 {@code ByGrandInviter...} / {@code ByInviter...} 等向上追溯入口）；</li>
 *   <li>{@code @Query} 注解（JPQL/原生 SQL）不得以 {@code grand_inviter}/{@code inviter} 为 WHERE 条件向上递归。</li>
 * </ul>
 * 仓储仅允许按受益人本人（beneficiary_id）、被推荐人本人（referee_id，用于按人封顶一次）或触发订单（order_id）查询。
 * 新增仓储查询方法前请先通过本守卫。
 */
class PendingRebateRepositoryL3GuardTest {

    /** Spring Data 派生查询里「以某字段为条件」的关键字前缀。 */
    private static final List<String> CONDITION_PREFIXES = List.of("findBy", "getBy", "queryBy",
            "readBy", "countBy", "existsBy", "deleteBy", "removeBy", "streamBy");

    /** 关系链追溯字段：以这些为查询条件即可形成向上递归追溯，严禁。 */
    private static final List<String> FORBIDDEN_CONDITION_TOKENS = List.of("grandinviter", "inviter");

    @Test
    void pendingRebateRepository_hasNoReferralChainQueryCondition() {
        for (Method m : PendingRebateRepository.class.getDeclaredMethods()) {
            String name = m.getName();
            boolean isDerivedQuery = CONDITION_PREFIXES.stream().anyMatch(name::startsWith);
            if (isDerivedQuery) {
                String lower = name.toLowerCase();
                for (String token : FORBIDDEN_CONDITION_TOKENS) {
                    assertThat(lower)
                            .as("仓储派生查询方法 %s 不得以关系链字段 '%s' 为查询条件（L3+ 物理隔离）", name, token)
                            .doesNotContain(token);
                }
            }
            Query q = m.getAnnotation(Query.class);
            if (q != null) {
                String lower = q.value().toLowerCase();
                assertThat(lower)
                        .as("仓储 @Query 方法 %s 不得以 grand_inviter 为查询条件（L3+ 物理隔离）", name)
                        .doesNotContain("grand_inviter")
                        .doesNotContain("grandinviter");
            }
        }
    }

    @Test
    void pendingRebateRepository_exposesOnlyBeneficiaryRefereeOrderAndStatusLookups() {
        // 正向断言：仅暴露按受益人本人 / 被推荐人本人 / 触发订单 / 解冻状态的查询。
        // referee_id 是被推荐人本人（封顶用），不是关系链上层 id，故不构成 L3+ 追溯。
        List<String> declared = java.util.Arrays.stream(
                        PendingRebateRepository.class.getDeclaredMethods())
                .map(Method::getName)
                .toList();
        assertThat(declared).containsExactlyInAnyOrder(
                "findByBeneficiaryIdOrderByFrozenAtDesc",
                "findByStatusAndFrozenAtBefore",
                "findByOrderId",
                "findByRefereeId");
    }
}
