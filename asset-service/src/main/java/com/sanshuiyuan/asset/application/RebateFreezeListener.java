package com.sanshuiyuan.asset.application;

import com.sanshuiyuan.asset.application.event.RebateFreezeRequested;
import com.sanshuiyuan.asset.domain.Sku;
import com.sanshuiyuan.asset.infra.client.UserServiceClient;
import com.sanshuiyuan.asset.infra.client.UserServiceClient.ReferralChain;
import com.sanshuiyuan.asset.infra.repository.SkuRepository;
import com.sanshuiyuan.asset.rebate.application.RebateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 推荐返利冻结监听器：购机支付主事务<b>提交后</b>异步执行（仿 {@link OwnerRoleGrantListener} D.2.4）。
 *
 * <p>流程：
 * <ol>
 *   <li>调 user-service 取被推荐人的两级关系链 {inviterId(L1), grandInviterId(L2)}（失败返回空，不抛）；</li>
 *   <li>读触发订单 SKU 的固定介绍费 {@code referral_fee_l1/l2_cents}（金额快照，之后改费率不影响已冻结记录）；</li>
 *   <li>调 {@link RebateService#freezeForReferee}，按「每人仅一次」封顶冻结 L1/L2。</li>
 * </ol>
 *
 * <p><b>合规铁律：</b>本监听器只把 user-service 返回的两个 id 透传给领域服务，自身不做任何关系链
 * 递归/追溯（L3+ 物理隔离）。异常仅记录，绝不影响已提交的支付主流程。
 */
@Component
public class RebateFreezeListener {

    private static final Logger log = LoggerFactory.getLogger(RebateFreezeListener.class);

    private final UserServiceClient userServiceClient;
    private final SkuRepository skuRepository;
    private final RebateService rebateService;

    public RebateFreezeListener(UserServiceClient userServiceClient,
                                SkuRepository skuRepository,
                                RebateService rebateService) {
        this.userServiceClient = userServiceClient;
        this.skuRepository = skuRepository;
        this.rebateService = rebateService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRebateFreezeRequested(RebateFreezeRequested event) {
        try {
            ReferralChain chain = userServiceClient.getReferralChain(event.refereeUserId());
            // 自然流量 / 无上级：两级均空则无介绍费，直接返回。
            if (chain == null || (chain.inviterId() == null && chain.grandInviterId() == null)) {
                return;
            }

            Sku sku = skuRepository.findById(event.skuId()).orElse(null);
            if (sku == null) {
                log.warn("推荐返利冻结跳过：SKU 不存在 skuId={} orderId={}", event.skuId(), event.orderId());
                return;
            }
            // 金额快照：按机型固定介绍费，绝非购机款百分比。
            Long l1Fee = sku.getReferralFeeL1Cents();
            Long l2Fee = sku.getReferralFeeL2Cents();

            rebateService.freezeForReferee(
                    event.orderId(),
                    event.refereeUserId(),
                    chain.inviterId(),
                    chain.grandInviterId(),
                    l1Fee,
                    l2Fee);
        } catch (Exception e) {
            // 支付主事务已提交；返利冻结失败不得反噬支付。ALERT 级别便于对账补偿检索。
            log.error("ALERT: 推荐返利冻结失败 orderId={} referee={}: {}",
                    event.orderId(), event.refereeUserId(), e.getMessage(), e);
        }
    }
}
