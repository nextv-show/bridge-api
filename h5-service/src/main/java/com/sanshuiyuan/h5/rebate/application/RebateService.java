package com.sanshuiyuan.h5.rebate.application;

import com.sanshuiyuan.h5.rebate.domain.PendingRebate;
import com.sanshuiyuan.h5.rebate.domain.RebateLevel;
import com.sanshuiyuan.h5.rebate.infra.repository.PendingRebateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 返利冻结领域服务（011-rebate-freeze）。
 *
 * <p>承载状态机的全部副作用：支付成功冻结、冷静期满确认、退款取消。
 *
 * <p><b>合规铁律：</b>仅按订单快照的 L1/L2 受益人冻结，<b>绝不</b>以受益人 id 向上/向下递归追溯关系链（L3+ 物理隔离）。
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
     * 支付成功后冻结返利：按订单下单时刻快照的 L1（inviter_id）/ L2（grand_inviter_id）各冻结一条 FROZEN。
     *
     * <p>仅 L1+L2，严禁 L3+：入参只接受订单已快照的两个 id，本方法不做任何关系链查询。
     * 幂等：同 (order, beneficiary, level) 已存在则跳过（防重复回调重复冻结）。
     * 自然流量订单（inviterId/grandInviterId 均为 null）不产生任何记录。
     *
     * @param orderId        触发返利的订单 id
     * @param inviterId      L1 直接邀请人 H5 user_id（可 null）
     * @param grandInviterId L2 间接邀请人 H5 user_id（可 null）
     */
    @Transactional
    public void freezeForOrder(Long orderId, Long inviterId, Long grandInviterId) {
        Set<RebateLevel> existing = repo.findByOrderId(orderId).stream()
                .map(PendingRebate::getLevel)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(RebateLevel.class)));

        if (inviterId != null && !existing.contains(RebateLevel.L1)) {
            repo.save(PendingRebate.freeze(orderId, inviterId, RebateLevel.L1, props.getL1AmountCents()));
            log.info("返利冻结 orderId={} level=L1 beneficiary={}", orderId, inviterId);
        }
        if (grandInviterId != null && !existing.contains(RebateLevel.L2)) {
            repo.save(PendingRebate.freeze(orderId, grandInviterId, RebateLevel.L2, props.getL2AmountCents()));
            log.info("返利冻结 orderId={} level=L2 beneficiary={}", orderId, grandInviterId);
        }
        // 严禁 L3+：订单快照本就不含更上层 id，此处不做任何向上递归。
    }
}
