package com.sanshuiyuan.asset.rebate.infra.repository;

import com.sanshuiyuan.asset.rebate.domain.PendingRebate;
import com.sanshuiyuan.asset.rebate.domain.RebateStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 推荐返利冻结台账仓储。
 *
 * <p><b>L3+ 物理隔离：</b>仅按 beneficiary_id（受益人本人）、referee_id（被推荐人本人）或 order_id 查询。
 * 严禁以受益人 id 作为「邀请人」反向递归追溯下游关系链（无任何 ByGrandInviter / grand_inviter 查询）。
 */
public interface PendingRebateRepository extends JpaRepository<PendingRebate, Long> {

    /** 当前用户作为受益人的全部返利（按冻结时间倒序），供 /rebates 列表。 */
    List<PendingRebate> findByBeneficiaryIdOrderByFrozenAtDesc(Long beneficiaryId);

    /** 解冻定时任务：冻结早于阈值（frozen_at + 冷静期 <= now）且仍为指定状态的记录。 */
    List<PendingRebate> findByStatusAndFrozenAtBefore(RebateStatus status, LocalDateTime before);

    /** 退款取消：某触发订单下的全部返利记录。 */
    List<PendingRebate> findByOrderId(Long orderId);

    /**
     * 「每人仅一次」封顶：某被推荐人已存在的全部返利记录（用于判定其各层级是否已冻结过）。
     * 注意：referee_id 是「被推荐人本人」，不是关系链上层 id，故不构成 L3+ 追溯。
     */
    List<PendingRebate> findByRefereeId(Long refereeId);
}
