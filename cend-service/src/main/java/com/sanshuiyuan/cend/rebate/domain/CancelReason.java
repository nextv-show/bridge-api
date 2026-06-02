package com.sanshuiyuan.cend.rebate.domain;

/**
 * 返利取消原因。取消均由「实体商品买卖合同解除（退款）」触发——
 * 严禁以「理财赎回 / 保本退出」等金融化语义解读。
 */
public enum CancelReason {
    /** 冷静期内退款：对应 FROZEN→CANCELLED。 */
    REFUND_COOLDOWN,
    /** 冷静期后退款：对应 CONFIRMED→CANCELLED。 */
    REFUND_POST_COOLDOWN
}
