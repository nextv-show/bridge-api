package com.sanshuiyuan.user.referral;

import com.sanshuiyuan.user.infra.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T8b.5：L3+ 物理隔离守卫（合规铁律，自 h5-service 移植）。
 *
 * <p>以反射断言：承载关系链的 {@link UserRepository} <b>不得</b>提供任何能形成「邀请人的邀请人的…」
 * 三级以上链路追溯的查询：
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

    /** user-service Flyway 迁移目录的相对路径（相对模块根）。 */
    private static final Path MIGRATION_RELATIVE =
            Path.of("src", "main", "resources", "db", "migration");

    /**
     * 稳定定位 Flyway 迁移目录，<b>不依赖绝对路径</b>。
     *
     * <p>Gradle 默认以模块目录（{@code user-service/}）为测试工作目录，但从仓库根（{@code src/api/}）运行时
     * 工作目录为根。两种情况都以相对路径命中：先试模块相对路径，再试 {@code user-service/} 前缀，最终回退。
     */
    private static Path migrationDir() {
        Path moduleRelative = MIGRATION_RELATIVE;
        if (Files.isDirectory(moduleRelative)) {
            return moduleRelative;
        }
        Path repoRootRelative = Path.of("user-service").resolve(MIGRATION_RELATIVE);
        if (Files.isDirectory(repoRootRelative)) {
            return repoRootRelative;
        }
        return moduleRelative;
    }

    @Test
    void userRepository_hasNoGrandInviterQueryMethod() {
        assertNoGrandInviterQueryCondition(UserRepository.class);
    }

    @Test
    void userRepository_exposesOnlyAllowedLookups() {
        // 正向断言：UserRepository 仅暴露按 unionid/openid 的单点查询与按 inviter_id 的 L1 单层正向查询
        // （继承自 JpaRepository 的 findById 等不计入声明方法）。getDeclaredMethods 顺序不保证，故用 InAnyOrder。
        List<String> declared = Arrays.stream(UserRepository.class.getDeclaredMethods())
                .map(Method::getName)
                .toList();
        assertThat(declared).containsExactlyInAnyOrder(
                "findByUnionid", "findByOpenidWx", "findByOpenidApp", "findByInviterId");
    }

    /**
     * 迁移层守卫：V004 历史迁移允许出现 {@code CREATE INDEX idx_grand_inviter}（历史迁移不可改），
     * 但必须由后续迁移 {@code V005__drop_grand_inviter_index.sql} 删除该索引，
     * 从 DB 层堵死「按 grand_inviter_id 形成 L3+ 查询能力」的隐患。
     */
    @Test
    void migration_dropsGrandInviterIndexAfterV004() throws IOException {
        Path migrationDir = migrationDir();
        // V004 历史迁移：允许存在 idx_grand_inviter 创建语句（历史迁移不可修改，避免 Flyway checksum 漂移）。
        Path v004 = migrationDir.resolve("V004__add_referral_chain.sql");
        assertThat(Files.exists(v004))
                .as("历史迁移 %s 必须存在", v004)
                .isTrue();
        String v004Sql = Files.readString(v004, StandardCharsets.UTF_8).toLowerCase();
        assertThat(v004Sql)
                .as("V004 作为历史迁移，仍保留其原始的 idx_grand_inviter 创建语句（不得回改历史迁移）")
                .contains("create index idx_grand_inviter");

        // V005 后续迁移：必须删除 idx_grand_inviter，且不得重新创建 grand_inviter 索引。
        Path v005 = migrationDir.resolve("V005__drop_grand_inviter_index.sql");
        assertThat(Files.exists(v005))
                .as("必须存在后续迁移 %s 以删除旧推荐链 grand_inviter 索引", v005)
                .isTrue();
        String v005Sql = Files.readString(v005, StandardCharsets.UTF_8).toLowerCase();
        assertThat(v005Sql)
                .as("V005 必须删除 idx_grand_inviter 索引（DB 层堵死 L3+ 查询能力）")
                .contains("drop index idx_grand_inviter on users");
        assertThat(v005Sql)
                .as("V005 不得重新创建 grand_inviter 索引")
                .doesNotContain("create index idx_grand_inviter")
                .doesNotContain("create index idx_grandinviter");
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
