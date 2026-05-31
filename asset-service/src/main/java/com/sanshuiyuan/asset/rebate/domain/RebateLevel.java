package com.sanshuiyuan.asset.rebate.domain;

/**
 * 返利受益层级。<b>合规铁律：仅 L1（直接邀请人）+ L2（间接邀请人）两级，严禁 L3+。</b>
 * 取值来源于触发返利时由 user-service 一次性返回的关系链 {inviterId, grandInviterId}
 * （绝不在 asset-service 内向上递归追溯）。
 */
public enum RebateLevel {
    L1, L2
}
