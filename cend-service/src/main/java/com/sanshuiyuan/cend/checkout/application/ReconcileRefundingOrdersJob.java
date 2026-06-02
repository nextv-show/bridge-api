package com.sanshuiyuan.cend.checkout.application;

import com.sanshuiyuan.cend.checkout.domain.CendOrder;
import com.sanshuiyuan.cend.checkout.domain.OrderStatus;
import com.sanshuiyuan.cend.checkout.domain.Refund;
import com.sanshuiyuan.cend.checkout.domain.RefundStatus;
import com.sanshuiyuan.cend.checkout.infra.repository.CendOrderRepository;
import com.sanshuiyuan.cend.checkout.infra.repository.RefundRepository;
import com.sanshuiyuan.cend.checkout.infra.wxpay.WxRefundClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 主动查询退款结果兜底（refund reconcile）：微信退款异步回调不达时，
 * 定时轮询 {@link OrderStatus#REFUNDING} 订单，查到 SUCCESS / CLOSED 即按回调相同逻辑
 * 完成退款（{@link RefundService#applyRefundResult}）。
 *
 * <p>与 {@link ReconcilePendingOrdersJob} 同形：30s 一轮、最近 10s 内新发起的退款跳过、
 * 24h 窗口外的不再查询（异常单留人工介入）。
 */
@Component
@ConditionalOnProperty(name = "h5.refund-reconcile.enabled", havingValue = "true", matchIfMissing = true)
public class ReconcileRefundingOrdersJob {

    private static final Logger log = LoggerFactory.getLogger(ReconcileRefundingOrdersJob.class);

    /** 查询窗口：仅核对最近 24h 内发起的退款。 */
    private static final long WINDOW_HOURS = 24;
    /** 跳过最近 10s 内新发起的退款，避免对 WxRefundClient.refund() 刚下达指令、微信尚未生效的单做无谓查询。 */
    private static final long SKIP_RECENT_SECONDS = 10;

    private final CendOrderRepository orderRepo;
    private final RefundRepository refundRepo;
    private final WxRefundClient wxRefundClient;
    private final RefundService refundService;

    public ReconcileRefundingOrdersJob(CendOrderRepository orderRepo, RefundRepository refundRepo,
                                       WxRefundClient wxRefundClient, RefundService refundService) {
        this.orderRepo = orderRepo;
        this.refundRepo = refundRepo;
        this.wxRefundClient = wxRefundClient;
        this.refundService = refundService;
    }

    @Scheduled(fixedDelay = 30_000)
    public void reconcile() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.minusHours(WINDOW_HOURS);
        LocalDateTime skipAfter = now.minusSeconds(SKIP_RECENT_SECONDS);

        List<CendOrder> refunding = orderRepo.findByStatus(OrderStatus.REFUNDING);
        for (CendOrder order : refunding) {
            try {
                Refund refund = refundRepo.findByOrderId(order.getId()).orElse(null);
                if (refund == null) {
                    log.warn("订单 status=REFUNDING 但未找到 refund 记录 orderNo={}（数据异常，跳过）",
                            order.getOrderNo());
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
                    // PROCESSING / ABNORMAL：本轮不下结论。
                    continue;
                }
                log.info("主动查询退款命中：orderNo={} refundNo={} success={} wxRefundId={}，执行兜底完成",
                        order.getOrderNo(), refund.getRefundNo(), r.success(), r.wxRefundId());
                refundService.applyRefundResult(r);
            } catch (Exception e) {
                // 单笔失败不中断整批。
                log.error("主动查询退款对账失败 orderNo={}: {}", order.getOrderNo(), e.getMessage(), e);
            }
        }
    }
}
