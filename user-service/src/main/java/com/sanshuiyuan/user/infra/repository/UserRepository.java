package com.sanshuiyuan.user.infra.repository;

import com.sanshuiyuan.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 用户仓储。
 *
 * <p><b>L3+ 物理隔离守卫（合规铁律）</b>：本仓储承载关系链，<b>只允许</b>按 {@code unionid}/{@code openid}/主键
 * {@code id} 单点查询，或以 {@code inviter_id} 作<b>单层（L1）</b>正向查询「我直接推荐的人」；
 * <b>严禁</b>提供任何以 {@code grand_inviter_id} 为查询条件、或能形成「邀请人的邀请人的…」三级以上链路追溯的方法
 * （如 {@code findByGrandInviterId}、批量上溯式 {@code findByInviterIdIn} 递归等）。
 * {@code grand_inviter_id} 仅作单条记录的一次性快照读取，绝不用于向上递归。
 *
 * <p><b>DB 层物理隔离</b>：V004 曾为旧推荐链创建 {@code idx_grand_inviter}，已由
 * {@code V005__drop_grand_inviter_index.sql} 删除；{@code grand_inviter_id} 在 DB 层不再有索引，
 * 仅作单条快照列，不作为查询入口，从存储层进一步堵死 L3+ 链路追溯能力。
 *
 * <p>该约束由 {@code ReferralRepositoryL3GuardTest} 以反射 + 迁移文件断言守卫，新增查询方法前请先阅读该测试。
 */
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUnionid(String unionid);
    Optional<User> findByOpenidWx(String openidWx);
    Optional<User> findByOpenidApp(String openidApp);

    /**
     * 查询「我直接推荐的人」——以 {@code inviter_id} 为条件的 <b>L1 单层正向查询</b>（合规允许，015 我的推荐）。
     * 仅向下一层展开，不触及 {@code grand_inviter_id}，绝不形成 L3+ 链路追溯。
     */
    List<User> findByInviterId(Long inviterId);
}
