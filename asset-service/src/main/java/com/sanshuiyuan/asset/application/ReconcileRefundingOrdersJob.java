package com.sanshuiyuan.asset.application;

import com.sanshuiyuan.asset.domain.Order;
import com.sanshuiyuan.asset.domain.OrderStatus;
import com.sanshuiyuan.asset.domain.Refund;
import com.sanshuiyuan.asset.domain.RefundStatus;
import com.sanshuiyuan.asset.infra.repository.OrderRepository;
import com.sanshuiyuan.asset.infra.repository.RefundRepository;
import com.sanshuiyuan.asset.infra.wxpay.WxRefundClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 购机退款主动查单兜底（移植自 h5-service）：微信退款异步回调不达时，30s 一轮轮询
 * {@link OrderStatus#REFUNDING} 订单，查到 SUCCESS / CLOSED 即按回调相同逻辑完成
 * （{@link PurchaseRefundService#applyRefundResult}）。本机基建上退款回调零送达，此兜底为关键路径。
 *
 * <p>最近 10s 内新发起的退款跳过（微信尚未生效）；24h 窗口外不再查询（异常单留人工介入）。
 */
@Component
@ConditionalOnProperty(name = "asset.refund-reconcile.enabled", havingValue = "true", matchIfMissing = true)
public class ReconcileRefundingOrdersJob {

    private static final Logger log = LoggerFactory.getLogger(ReconcileRefundingOrdersJob.class);

    /** 查询窗口：仅核对最近 24h 内发起的退款。 */
    private static final long WINDOW_HOURS = 24;
    /** 跳过最近 10s 内新发起的退款，避免对刚下达、微信尚未生效的单做无谓查询。 */
    private static final long SKIP_RECENT_SECONDS = 10;

    private final OrderRepository orderRepository;
    private final RefundRepository refundRepository;
    private final WxRefundClient wxRefundClient;
    private final PurchaseRefundService purchaseRefundService;

    public ReconcileRefundingOrdersJob(OrderRepository orderRepository, RefundRepository refundRepository,
                                       WxRefundClient wxRefundClient, PurchaseRefundService purchaseRefundService) {
        this.orderRepository = orderRepository;
        this.refundRepository = refundRepository;
        this.wxRefundClient = wxRefundClient;
        this.purchaseRefundService = purchaseRefundService;
    }

    @Scheduled(fixedDelay = 30_000)
    public void reconcile() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.minusHours(WINDOW_HOURS);
        LocalDateTime skipAfter = now.minusSeconds(SKIP_RECENT_SECONDS);

        List<Order> refunding = orderRepository.findByStatus(OrderStatus.REFUNDING);
        for (Order order : refunding) {
            try {
                Refund refund = refundRepository.findByOrderId(order.getId()).orElse(null);
                if (refund == null) {
                    log.warn("订单 status=REFUNDING 但未找到 refund 记录 orderId={}（数据异常，跳过）",
                            order.getId());
                    continue;
                }
                if (refund.getStatus() != RefundStatus.PROCESSING) {
                    continue; // 已 SUCCESS / FAILED 不重查
                }

                LocalDateTime createdAt = refund.getCreatedAt();
                if (createdAt != null) {
                    if (createdAt.isBefore(windowStart)) {
                        continue; // 超 24h 窗口的留待人工介入
                    }
                    if (createdAt.isAfter(skipAfter)) {
                        continue; // 太新，等等再查
                    }
                }
                // createdAt 为 null 一并核对（保守包含）。

                WxRefundClient.RefundCallbackResult r = wxRefundClient.queryRefund(refund.getRefundNo());
                if (r == null) {
                    continue; // PROCESSING / ABNORMAL：本轮不下结论。
                }
                log.info("主动查询退款命中：orderId={} refundNo={} success={} wxRefundId={}，执行兜底完成",
                        order.getId(), refund.getRefundNo(), r.success(), r.wxRefundId());
                purchaseRefundService.applyRefundResult(r);
            } catch (Exception e) {
                log.error("主动查询退款对账失败 orderId={}: {}", order.getId(), e.getMessage(), e);
            }
        }
    }
}
