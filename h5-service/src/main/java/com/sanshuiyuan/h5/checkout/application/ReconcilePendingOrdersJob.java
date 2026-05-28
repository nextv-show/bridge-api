package com.sanshuiyuan.h5.checkout.application;

import com.sanshuiyuan.h5.checkout.domain.H5Order;
import com.sanshuiyuan.h5.checkout.domain.OrderStatus;
import com.sanshuiyuan.h5.checkout.infra.repository.H5OrderRepository;
import com.sanshuiyuan.h5.checkout.infra.wxpay.WxPayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 主动查单兜底（reconcile）：微信异步支付回调不达时，定时轮询 PENDING_PAY 订单，
 * 查单结果为 SUCCESS 则按回调相同逻辑完成支付（{@link OrderPaymentCompletionService}）。
 *
 * <p>仅处理「转已支付」，绝不在此关单/退款（过期关单由 CloseExpiredOrdersJob 负责）。
 */
@Component
@ConditionalOnProperty(name = "h5.pay-reconcile.enabled", havingValue = "true", matchIfMissing = true)
public class ReconcilePendingOrdersJob {

    private static final Logger log = LoggerFactory.getLogger(ReconcilePendingOrdersJob.class);

    /** 查单窗口：仅核对最近 24h 内创建的订单。 */
    private static final long WINDOW_HOURS = 24;
    /** 安全间隔：跳过最近 15s 内创建的订单，避免对用户尚在收银台、还没支付的新单做无谓查单。 */
    private static final long SKIP_RECENT_SECONDS = 15;

    private final H5OrderRepository orderRepo;
    private final WxPayClient wxPayClient;
    private final OrderPaymentCompletionService completionService;

    public ReconcilePendingOrdersJob(H5OrderRepository orderRepo, WxPayClient wxPayClient,
                                     OrderPaymentCompletionService completionService) {
        this.orderRepo = orderRepo;
        this.wxPayClient = wxPayClient;
        this.completionService = completionService;
    }

    @Scheduled(fixedDelay = 30_000)
    public void reconcile() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.minusHours(WINDOW_HOURS);
        LocalDateTime skipAfter = now.minusSeconds(SKIP_RECENT_SECONDS);

        List<H5Order> pending = orderRepo.findByStatus(OrderStatus.PENDING_PAY);
        for (H5Order order : pending) {
            try {
                LocalDateTime createdAt = order.getCreatedAt();
                if (createdAt != null) {
                    // 超出 24h 窗口的不查（交由关单任务处理）；最近 60s 内的跳过，避免竞争。
                    if (createdAt.isBefore(windowStart)) {
                        continue;
                    }
                    if (createdAt.isAfter(skipAfter)) {
                        continue;
                    }
                }
                // createdAt 为 null 时一并核对（保守包含，避免漏单）。

                WxPayClient.TradeQueryResult r = wxPayClient.queryOrder(order.getOrderNo());
                if ("SUCCESS".equals(r.tradeState())) {
                    log.info("主动查单命中已支付：orderNo={} transactionId={} successTime={}，执行兜底完成支付",
                            order.getOrderNo(), r.transactionId(), r.successTime());
                    completionService.completePaid(order, r.transactionId(), "{\"recon\":\"active-query\"}");
                } else {
                    log.debug("主动查单 orderNo={} tradeState={}（不改单）", order.getOrderNo(), r.tradeState());
                }
            } catch (Exception e) {
                // 单笔失败不中断整批。
                log.error("主动查单对账失败 orderNo={}: {}", order.getOrderNo(), e.getMessage(), e);
            }
        }
    }
}
