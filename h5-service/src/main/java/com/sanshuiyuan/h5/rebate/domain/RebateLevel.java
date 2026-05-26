package com.sanshuiyuan.h5.rebate.domain;

/**
 * 返利受益层级。<b>合规铁律：仅 L1（直接邀请人）+ L2（间接邀请人）两级，严禁 L3+。</b>
 * 取值来源于订单下单时刻快照：L1 = inviter_id，L2 = grand_inviter_id（绝不向上递归追溯）。
 */
public enum RebateLevel {
    L1, L2
}
