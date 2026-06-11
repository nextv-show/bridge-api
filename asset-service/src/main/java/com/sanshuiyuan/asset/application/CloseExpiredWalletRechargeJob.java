package com.sanshuiyuan.asset.application;

import com.sanshuiyuan.asset.api.WalletPayController;
import com.sanshuiyuan.asset.domain.RechargeStatus;
import com.sanshuiyuan.asset.domain.WalletRecharge;
import com.sanshuiyuan.asset.infra.repository.WalletRechargeRepository;
import com.sanshuiyuan.asset.infra.wxpay.MpWxPayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 待支付水费充值单超时关单（兜底 {@link ReconcileWalletRechargeJob}）。
 *
 * <p>背景：用户在收银台放弃支付后，充值单永久卡在 {@link RechargeStatus#PENDING_PAY}——
 * 既不会被回调/主动查单转 PAID（因为根本没付），账单页又长期堆积废单。采购单有
 * {@link CloseExpiredOrdersJob} 关单，但 wallet_recharge 此前没有对应任务。
 *
 * <p>本任务 5min 一轮，拉取创建超过 24h 仍待支付的充值单：
 * <ol>
 *   <li><b>资损防护</b>：关单前先按商户订单号主动查微信一次，查到 SUCCESS 则兜底入账
 *       （{@link WalletService#markPaidByRecharge}，幂等）而非关单；</li>
 *   <li>否则作废为 {@link RechargeStatus#CANCELLED}（复用 {@link WalletService#cancelRecharge}，
 *       仍走归属/状态校验，幂等）。</li>
 * </ol>
 * 与 {@link ReconcileWalletRechargeJob} 互补：后者只查 24h <b>窗口内</b>的单转已支付，本任务接手
 * 窗口外的单做「查一次 → 入账或关单」收口。stub 客户端 queryOrder 返回 "STUB" 不会误入账。
 */
@Component
@ConditionalOnProperty(name = "wallet.pay-expire.enabled", havingValue = "true", matchIfMissing = true)
public class CloseExpiredWalletRechargeJob {

    private static final Logger log = LoggerFactory.getLogger(CloseExpiredWalletRechargeJob.class);

    /** 关单门槛：创建超过 24h 仍待支付即视为超时（与采购单 CloseExpiredOrdersJob 一致）。 */
    private static final long EXPIRE_HOURS = 24;

    private final WalletRechargeRepository rechargeRepo;
    private final MpWxPayClient mpWxPayClient;
    private final WalletService walletService;

    public CloseExpiredWalletRechargeJob(WalletRechargeRepository rechargeRepo, MpWxPayClient mpWxPayClient,
                                         WalletService walletService) {
        this.rechargeRepo = rechargeRepo;
        this.mpWxPayClient = mpWxPayClient;
        this.walletService = walletService;
    }

    @Scheduled(fixedDelay = 300_000)
    public void closeExpired() {
        LocalDateTime expiryTime = LocalDateTime.now().minusHours(EXPIRE_HOURS);
        List<WalletRecharge> expired = rechargeRepo.findByStatusAndCreatedAtBefore(RechargeStatus.PENDING_PAY, expiryTime);
        for (WalletRecharge r : expired) {
            try {
                // 关单前主动查单：查到已支付即兜底入账，避免「用户已付但被关单、钱包不增」资损。
                try {
                    MpWxPayClient.TradeQueryResult q = mpWxPayClient.queryOrder(WalletPayController.outTradeNo(r.getId()));
                    if ("SUCCESS".equals(q.tradeState())) {
                        log.info("超时关单时查到微信已支付，转兜底入账：rechargeId={} transactionId={}", r.getId(), q.transactionId());
                        walletService.markPaidByRecharge(r.getId(), q.transactionId());
                        continue;
                    }
                } catch (RuntimeException e) {
                    // 查单失败（如订单过老被微信清理）按未支付处理，继续关单。
                    log.warn("超时关单 rechargeId={} 前查单失败，按未支付关单：{}", r.getId(), e.getMessage());
                }
                walletService.cancelRecharge(r.getUserId(), r.getId());
                log.info("待支付充值单超时关单：rechargeId={} userId={} createdAt={}", r.getId(), r.getUserId(), r.getCreatedAt());
            } catch (Exception e) {
                // 单笔失败不中断整批。
                log.error("待支付充值单超时关单失败 rechargeId={}: {}", r.getId(), e.getMessage(), e);
            }
        }
    }
}
