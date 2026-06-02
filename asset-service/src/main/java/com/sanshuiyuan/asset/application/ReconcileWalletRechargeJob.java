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
 * 小程序水费充值主动查单兜底（移植自 cend-service {@code ReconcilePendingOrdersJob}）：本机基建上
 * 微信支付异步回调零送达，仅靠 {@code /wxpay/wallet-callback} 入账会让充值单永远卡在 PENDING_PAY、
 * 钱包余额不增。此任务 30s 一轮轮询 {@link RechargeStatus#PENDING_PAY} 充值单，按商户订单号查微信，
 * 查到 SUCCESS 即按回调相同逻辑入账（{@link WalletService#markPaidByRecharge}，幂等）。
 *
 * <p>仅处理「转已支付」，绝不在此关单/作废。最近 15s 内新建的跳过（用户可能还在收银台）；
 * 24h 窗口外不再查询（异常单留人工介入）。stub 客户端 queryOrder 返回 "STUB" 不会误入账。
 */
@Component
@ConditionalOnProperty(name = "wallet.pay-reconcile.enabled", havingValue = "true", matchIfMissing = true)
public class ReconcileWalletRechargeJob {

    private static final Logger log = LoggerFactory.getLogger(ReconcileWalletRechargeJob.class);

    /** 查单窗口：仅核对最近 24h 内创建的充值单。 */
    private static final long WINDOW_HOURS = 24;
    /** 安全间隔：跳过最近 15s 内创建的充值单，避免对用户尚在收银台、还没支付的新单做无谓查单。 */
    private static final long SKIP_RECENT_SECONDS = 15;

    private final WalletRechargeRepository rechargeRepo;
    private final MpWxPayClient mpWxPayClient;
    private final WalletService walletService;

    public ReconcileWalletRechargeJob(WalletRechargeRepository rechargeRepo, MpWxPayClient mpWxPayClient,
                                      WalletService walletService) {
        this.rechargeRepo = rechargeRepo;
        this.mpWxPayClient = mpWxPayClient;
        this.walletService = walletService;
    }

    @Scheduled(fixedDelay = 30_000)
    public void reconcile() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.minusHours(WINDOW_HOURS);
        LocalDateTime skipAfter = now.minusSeconds(SKIP_RECENT_SECONDS);

        List<WalletRecharge> pending = rechargeRepo.findByStatus(RechargeStatus.PENDING_PAY);
        for (WalletRecharge r : pending) {
            try {
                LocalDateTime createdAt = r.getCreatedAt();
                if (createdAt != null) {
                    // 超出 24h 窗口的不查（留待人工介入）；最近 15s 内的跳过，避免竞争。
                    if (createdAt.isBefore(windowStart)) {
                        continue;
                    }
                    if (createdAt.isAfter(skipAfter)) {
                        continue;
                    }
                }
                // createdAt 为 null 时一并核对（保守包含，避免漏单）。

                String outTradeNo = WalletPayController.outTradeNo(r.getId());
                MpWxPayClient.TradeQueryResult q = mpWxPayClient.queryOrder(outTradeNo);
                if ("SUCCESS".equals(q.tradeState())) {
                    log.info("钱包充值主动查单命中已支付：rechargeId={} outTradeNo={} transactionId={} successTime={}，执行兜底入账",
                            r.getId(), outTradeNo, q.transactionId(), q.successTime());
                    walletService.markPaidByRecharge(r.getId(), q.transactionId());
                } else {
                    log.debug("钱包充值主动查单 rechargeId={} tradeState={}（不入账）", r.getId(), q.tradeState());
                }
            } catch (Exception e) {
                // 单笔失败不中断整批。
                log.error("钱包充值主动查单对账失败 rechargeId={}: {}", r.getId(), e.getMessage(), e);
            }
        }
    }
}
