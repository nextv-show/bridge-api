package com.sanshuiyuan.asset.rebate.application;

import com.sanshuiyuan.asset.rebate.api.dto.RebateSummary;
import com.sanshuiyuan.asset.rebate.domain.CancelReason;
import com.sanshuiyuan.asset.rebate.domain.PendingRebate;
import com.sanshuiyuan.asset.rebate.domain.RebateLevel;
import com.sanshuiyuan.asset.rebate.domain.RebateStatus;
import com.sanshuiyuan.asset.rebate.infra.repository.PendingRebateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 推荐返利领域服务（一次性实物销售介绍费）。
 *
 * <p>承载状态机的全部副作用：购机支付成功冻结、冷静期满确认、退款取消。
 *
 * <p><b>合规铁律：</b>
 * <ul>
 *   <li>仅按调用方传入的 L1/L2 受益人冻结，<b>绝不</b>以受益人 id 向上/向下递归追溯关系链（L3+ 物理隔离）；</li>
 *   <li>「每人仅一次」：封顶键为被推荐人 + 层级（{@code uk_referee_level}），同一被推荐人无论下几单，
 *       同一层级至多一条返利记录——与 H5「按订单」不同；</li>
 *   <li>金额为 SKU 固定费率的快照（调用方读 SKU 列后传入），严禁百分比。</li>
 * </ul>
 */
@Service
public class RebateService {

    private static final Logger log = LoggerFactory.getLogger(RebateService.class);

    private final PendingRebateRepository repo;
    private final RebateProperties props;

    public RebateService(PendingRebateRepository repo, RebateProperties props) {
        this.repo = repo;
        this.props = props;
    }

    /**
     * 购机支付成功后冻结返利：按被推荐人的 L1（直接邀请人）/ L2（间接邀请人）各冻结一条 FROZEN。
     *
     * <p>仅 L1+L2，严禁 L3+：入参只接受调用方已取好的两个 id，本方法不做任何关系链查询/递归。
     * 「每人仅一次」：以 {@code referee_id + level} 为封顶键——若该被推荐人已有对应层级记录则跳过
     * （无论触发订单是否相同；防同一人多次下单重复返利）。
     * 自然流量（inviterId/grandInviterId 均为 null）不产生任何记录。
     *
     * @param orderId        触发返利的购机订单 id（记录触发来源）
     * @param refereeId      被推荐人 user_id（= 购机下单人）
     * @param inviterId      L1 直接邀请人 user_id（可 null）
     * @param grandInviterId L2 间接邀请人 user_id（可 null）
     * @param l1FeeCents     L1 介绍费（分）—— 由调用方从触发订单的 SKU 列快照
     * @param l2FeeCents     L2 介绍费（分）—— 由调用方从触发订单的 SKU 列快照
     */
    @Transactional
    public void freezeForReferee(Long orderId, Long refereeId, Long inviterId, Long grandInviterId,
                                 Long l1FeeCents, Long l2FeeCents) {
        // 按人封顶一次：取该被推荐人已存在的层级集合（不是按订单）。
        Set<RebateLevel> existing = repo.findByRefereeId(refereeId).stream()
                .map(PendingRebate::getLevel)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(RebateLevel.class)));

        if (inviterId != null && !existing.contains(RebateLevel.L1)) {
            repo.save(PendingRebate.freeze(orderId, refereeId, inviterId, RebateLevel.L1, l1FeeCents));
            log.info("推荐返利冻结 orderId={} referee={} level=L1 beneficiary={} amount={}",
                    orderId, refereeId, inviterId, l1FeeCents);
        }
        if (grandInviterId != null && !existing.contains(RebateLevel.L2)) {
            repo.save(PendingRebate.freeze(orderId, refereeId, grandInviterId, RebateLevel.L2, l2FeeCents));
            log.info("推荐返利冻结 orderId={} referee={} level=L2 beneficiary={} amount={}",
                    orderId, refereeId, grandInviterId, l2FeeCents);
        }
        // 严禁 L3+：仅接受传入的两个 id，此处不做任何向上递归。
    }

    /**
     * 解冻：将冷静期已满（frozen_at + cooldownHours <= now）的 FROZEN 返利确认为 CONFIRMED。
     * 由定时任务周期调用；状态流转由 {@link PendingRebate#confirm()} 守卫。
     *
     * @return 本次确认的记录数
     */
    @Transactional
    public int confirmExpired() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(props.getCooldownHours());
        List<PendingRebate> due = repo.findByStatusAndFrozenAtBefore(RebateStatus.FROZEN, threshold);
        int confirmed = 0;
        for (PendingRebate r : due) {
            r.confirm();
            repo.save(r);
            confirmed++;
        }
        if (confirmed > 0) {
            log.info("推荐返利解冻确认 {} 条（冷静期 {}h 已满）", confirmed, props.getCooldownHours());
        }
        return confirmed;
    }

    /**
     * 退款取消：实体商品买卖合同解除（购机退款成功）时，取消该订单下的全部返利。
     * 按返利当前状态决定取消原因——
     * <ul>
     *   <li>FROZEN（冷静期内退款）→ CANCELLED(REFUND_COOLDOWN)；</li>
     *   <li>CONFIRMED（冷静期后退款）→ CANCELLED(REFUND_POST_COOLDOWN)；</li>
     *   <li>已 CANCELLED → 幂等跳过。</li>
     * </ul>
     *
     * @return 本次取消的记录数
     */
    @Transactional
    public int cancelForRefund(Long orderId) {
        List<PendingRebate> rebates = repo.findByOrderId(orderId);
        int cancelled = 0;
        for (PendingRebate r : rebates) {
            switch (r.getStatus()) {
                case FROZEN -> {
                    r.cancel(CancelReason.REFUND_COOLDOWN);
                    repo.save(r);
                    cancelled++;
                }
                case CONFIRMED -> {
                    r.cancel(CancelReason.REFUND_POST_COOLDOWN);
                    repo.save(r);
                    cancelled++;
                }
                case CANCELLED -> {
                    // 幂等：重复退款不重复取消。
                }
            }
        }
        if (cancelled > 0) {
            log.info("退款取消推荐返利 orderId={} 共 {} 条", orderId, cancelled);
        }
        return cancelled;
    }

    /** 当前用户作为受益人的全部返利（按冻结时间倒序）。供 /rebates 列表。 */
    @Transactional(readOnly = true)
    public List<PendingRebate> listForBeneficiary(Long beneficiaryId) {
        return repo.findByBeneficiaryIdOrderByFrozenAtDesc(beneficiaryId);
    }

    /**
     * 返利摘要：已确认总额（仅 CONFIRMED 计入）、冻结中笔数、已取消笔数。
     * 合规：FROZEN 金额绝不计入总额。
     */
    @Transactional(readOnly = true)
    public RebateSummary summarize(Long beneficiaryId) {
        List<PendingRebate> all = repo.findByBeneficiaryIdOrderByFrozenAtDesc(beneficiaryId);
        long confirmedTotal = all.stream()
                .filter(r -> r.getStatus() == RebateStatus.CONFIRMED)
                .mapToLong(PendingRebate::getAmountCents)
                .sum();
        long frozenCount = all.stream().filter(r -> r.getStatus() == RebateStatus.FROZEN).count();
        long cancelledCount = all.stream().filter(r -> r.getStatus() == RebateStatus.CANCELLED).count();
        return new RebateSummary(confirmedTotal, frozenCount, cancelledCount);
    }
}
