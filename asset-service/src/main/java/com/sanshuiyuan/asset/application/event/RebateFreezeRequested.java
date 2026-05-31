package com.sanshuiyuan.asset.application.event;

/**
 * 推荐返利冻结请求事件：在购机订单转 PAID 的支付主事务内发布，被推荐人 = 购机下单人。
 *
 * <p>仿 {@link OwnerRoleGrantRequested} 的「提交后异步」模式——实际的关系链查询（user-service）
 * 与返利冻结被推迟到事务提交之后异步执行，使取关系链的下游故障永远无法回滚或阻塞支付主流程。
 *
 * @param orderId        触发返利的购机订单 id
 * @param refereeUserId  被推荐人 user_id（= 购机下单人 order.userId）
 * @param skuId          触发订单的机型 id（用于读 SKU 固定介绍费快照）
 */
public record RebateFreezeRequested(Long orderId, Long refereeUserId, Long skuId) {
}
