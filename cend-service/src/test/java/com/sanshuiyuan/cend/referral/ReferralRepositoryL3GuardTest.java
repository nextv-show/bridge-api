package com.sanshuiyuan.cend.referral;

import com.sanshuiyuan.cend.checkout.infra.repository.CendOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T8b.5：L3+ 物理隔离守卫（合规铁律）。
 *
 * <p>以反射断言：承载关系链的仓储<b>不得</b>提供任何能形成「邀请人的邀请人的…」三级以上链路追溯的查询：
 * <ul>
 *   <li>派生查询方法名不得以 {@code grand_inviter_id} 为查询条件（不含 {@code ByGrandInviter...} 等）；</li>
 *   <li>{@code @Query} 注解（JPQL/原生 SQL）不得以 {@code grand_inviter} 为 WHERE 条件向上递归。</li>
 * </ul>
 * {@code grand_inviter_id} 只允许作为单条记录的一次性快照被读取（getter），绝不作为查询入口。
 * 新增仓储查询方法前请先通过本守卫。
 */
class ReferralRepositoryL3GuardTest {

    /** Spring Data 派生查询里「以某字段为条件」的关键字前缀。 */
    private static final List<String> CONDITION_PREFIXES = List.of("findBy", "getBy", "queryBy",
            "readBy", "countBy", "existsBy", "deleteBy", "removeBy", "streamBy");

    @Test
    void h5UserRepository_hasNoGrandInviterQueryMethod() {
        assertNoGrandInviterQueryCondition(CendUserRepository.class);
    }

    @Test
    void h5OrderRepository_hasNoGrandInviterQueryMethod() {
        assertNoGrandInviterQueryCondition(CendOrderRepository.class);
    }

    @Test
    void h5UserRepository_exposesOnlyOpenidAndIdLookups() {
        // 正向断言：CendUserRepository 仅暴露按 openid 的单点查询与按 inviter_id 的 L1 单层正向查询
        // （继承自 JpaRepository 的 findById 等不计入声明方法）。getDeclaredMethods 顺序不保证，故用 InAnyOrder。
        List<String> declared = Arrays.stream(CendUserRepository.class.getDeclaredMethods())
                .map(Method::getName)
                .toList();
        assertThat(declared).containsExactlyInAnyOrder("findByOpenid", "findByInviterId");
    }

    private void assertNoGrandInviterQueryCondition(Class<?> repository) {
        for (Method m : repository.getDeclaredMethods()) {
            String name = m.getName();
            boolean isDerivedQuery = CONDITION_PREFIXES.stream().anyMatch(name::startsWith);
            if (isDerivedQuery) {
                assertThat(name.toLowerCase())
                        .as("仓储 %s 的派生查询方法 %s 不得以 grand_inviter 为查询条件（L3+ 物理隔离）",
                                repository.getSimpleName(), name)
                        .doesNotContain("grandinviter");
            }
            Query q = m.getAnnotation(Query.class);
            if (q != null) {
                assertThat(q.value().toLowerCase())
                        .as("仓储 %s 的 @Query 方法 %s 不得以 grand_inviter 为查询条件（L3+ 物理隔离）",
                                repository.getSimpleName(), name)
                        .doesNotContain("grand_inviter")
                        .doesNotContain("grandinviter");
            }
        }
    }
}
