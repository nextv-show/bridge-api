package com.sanshuiyuan.h5.referral;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * H5 用户关系链仓储。
 *
 * <p><b>L3+ 物理隔离守卫（合规铁律）</b>：本仓储<b>只允许</b>按 {@code openid} / 主键 {@code id} 单点查询，
 * <b>严禁</b>提供任何以 {@code grand_inviter_id} 为查询条件、或能形成「邀请人的邀请人的…」三级以上链路追溯的方法
 * （如 {@code findByGrandInviterId}、{@code findByInviterIdIn} 递归式批量上溯等）。
 * {@code grand_inviter_id} 仅作单条记录的一次性快照读取，绝不用于向上递归。
 *
 * <p>该约束由 {@code ReferralRepositoryL3GuardTest} 以反射断言守卫，新增查询方法前请先阅读该测试。
 */
public interface H5UserRepository extends JpaRepository<H5User, Long> {

    /** 按微信 openid 查询当前 H5 用户（登录/下单时定位本人，单点查询）。 */
    Optional<H5User> findByOpenid(String openid);
}
