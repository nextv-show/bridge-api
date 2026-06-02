package com.sanshuiyuan.cend.rebate.infra.repository;

import com.sanshuiyuan.cend.rebate.domain.PendingRebate;
import com.sanshuiyuan.cend.rebate.domain.RebateStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 返利冻结台账仓储。
 *
 * <p><b>L3+ 物理隔离：</b>仅按 beneficiary_id（受益人本人）或 order_id 查询，
 * 严禁以受益人 id 作为「邀请人」反向递归追溯下游关系链。
 */
public interface PendingRebateRepository extends JpaRepository<PendingRebate, Long> {

    /** 当前用户作为受益人的全部返利（按冻结时间倒序），供 /pending 列表。 */
    List<PendingRebate> findByBeneficiaryIdOrderByFrozenAtDesc(Long beneficiaryId);

    /** 解冻定时任务：冻结早于阈值（frozen_at + 冷静期 <= now）且仍为指定状态的记录。 */
    List<PendingRebate> findByStatusAndFrozenAtBefore(RebateStatus status, LocalDateTime before);

    /** 退款取消：某订单下的全部返利记录。 */
    List<PendingRebate> findByOrderId(Long orderId);

    /** 摘要：当前用户某状态返利计数。 */
    long countByBeneficiaryIdAndStatus(Long beneficiaryId, RebateStatus status);
}
