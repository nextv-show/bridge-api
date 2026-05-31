package com.sanshuiyuan.asset.application;

import com.sanshuiyuan.asset.api.dto.RefundResultDto;
import com.sanshuiyuan.asset.domain.Order;
import com.sanshuiyuan.asset.domain.OrderStatus;
import com.sanshuiyuan.asset.domain.Refund;
import com.sanshuiyuan.asset.domain.RefundStatus;
import com.sanshuiyuan.asset.infra.repository.OrderRepository;
import com.sanshuiyuan.asset.infra.repository.RefundRepository;
import com.sanshuiyuan.asset.infra.wxpay.WxRefundClient;
import com.sanshuiyuan.asset.rebate.application.RebateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 购机订单用户自助冷静期退款流程（移植自 h5-service RefundService，去掉 H5 专属投影/广播/模板消息）。
 *
 * <p>状态机：PAID --requestRefund--> REFUNDING --回调/查单成功--> REFUND；REFUNDING --失败--> PAID。
 *
 * <p>退款成功落地复用既有 {@link RefundService#handleRefundSucceeded}（置 REFUND + 联动取消返利），
 * 保持 S2S/人工端点不变。本服务额外负责：所有权/状态/冷静期校验、Refund 台账、微信发起、回调与查单兜底。
 *
 * <p>命名为 PurchaseRefundService 以避免与既有 {@link RefundService} 冲突。
 */
@Service
public class PurchaseRefundService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseRefundService.class);
    private static final DateTimeFormatter ISO_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final OrderRepository orderRepository;
    private final RefundRepository refundRepository;
    private final WxRefundClient wxRefundClient;
    private final RebateService rebateService;
    private final RefundService refundService;

    /** 冷静期（小时）。购机合同约定 24h。注入自 asset.refund.cooldown-hours。 */
    @Value("${asset.refund.cooldown-hours:24}")
    private long cooldownHours;

    public PurchaseRefundService(OrderRepository orderRepository,
                                 RefundRepository refundRepository,
                                 WxRefundClient wxRefundClient,
                                 RebateService rebateService,
                                 RefundService refundService) {
        this.orderRepository = orderRepository;
        this.refundRepository = refundRepository;
        this.wxRefundClient = wxRefundClient;
        this.rebateService = rebateService;
        this.refundService = refundService;
    }

    /** 测试可设冷静期窗口（生产由 @Value 注入）。 */
    void setCooldownHours(long cooldownHours) {
        this.cooldownHours = cooldownHours;
    }

    /**
     * 用户发起冷静期退款：校验所有权 / 状态 PAID / 冷静期未过 / 幂等，
     * 落 Refund(PROCESSING)、订单 PAID→REFUNDING，再向微信发起退款。
     * 微信异常 → Refund FAILED + 订单回退 PAID + 抛异常。
     */
    @Transactional
    public RefundResultDto requestRefund(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在"));

        if (!order.getUserId().equals(userId)) {
            throw new IllegalStateException("无权操作该订单");
        }

        if (order.getStatus() != OrderStatus.PAID) {
            throw new IllegalStateException("订单状态不可退款：" + order.getStatus());
        }

        // 冷静期 = paidAt + cooldownHours；过窗不可退（异常单留人工介入）。
        LocalDateTime paidAt = order.getPaidAt();
        if (paidAt != null) {
            LocalDateTime cooldownEnd = paidAt.plusHours(cooldownHours);
            if (LocalDateTime.now().isAfter(cooldownEnd)) {
                throw new IllegalStateException("已超过冷静期，不可退款");
            }
        }

        // 幂等：已存在退款记录直接回返（并发由 order_id 唯一约束兜底）。
        Refund existing = refundRepository.findByOrderId(orderId).orElse(null);
        if (existing != null) {
            return toDto(existing);
        }

        String refundNo = "RF" + System.currentTimeMillis() + orderId;
        Refund refund = Refund.create(orderId, refundNo, order.getAmountCents());

        try {
            refundRepository.save(refund);
        } catch (DataIntegrityViolationException e) {
            log.warn("并发退款请求 orderId={}", orderId);
            return refundRepository.findByOrderId(orderId)
                    .map(this::toDto)
                    .orElseThrow(() -> new IllegalStateException("退款记录冲突"));
        }

        order.setStatus(OrderStatus.REFUNDING);
        orderRepository.save(order);

        // out_trade_no 必须与支付时一致：购机 = 订单 id 左补零 10 位（见 OrderPayController）。
        String outTradeNo = String.format("%010d", order.getId());
        try {
            wxRefundClient.refund(outTradeNo, refundNo, order.getAmountCents());
        } catch (Exception e) {
            log.error("微信退款调用失败 orderId={}", orderId, e);
            refund.markFailed();
            order.setStatus(OrderStatus.PAID);
            refundRepository.save(refund);
            orderRepository.save(order);
            throw new IllegalStateException("退款发起失败，请稍后重试", e);
        }

        return toDto(refund);
    }

    /** 微信退款异步回调入口：验签解密后走统一落地。 */
    @Transactional
    public void handleRefundCallback(String body, String signature,
                                     String timestamp, String nonce, String serial) {
        log.info("收到微信退款回调");
        WxRefundClient.RefundCallbackResult result =
                wxRefundClient.parseCallback(body, signature, timestamp, nonce, serial);
        if (result == null) {
            log.warn("退款回调解析失败");
            return;
        }
        applyRefundResult(result);
    }

    /**
     * 应用微信侧退款结果。回调与查单兜底（{@link ReconcileRefundingOrdersJob}）共享此入口，幂等更新。
     *
     * <p>幂等：refund 已是 SUCCESS 时直接返回。
     * 成功 → Refund SUCCESS + 订单置 REFUND（复用 {@link RefundService#handleRefundSucceeded}，含联动取消返利）；
     * 失败 → Refund FAILED + 订单回退 PAID。
     */
    @Transactional
    public void applyRefundResult(WxRefundClient.RefundCallbackResult result) {
        refundRepository.findByRefundNo(result.refundNo()).ifPresent(refund -> {
            if (refund.getStatus() == RefundStatus.SUCCESS) {
                log.info("退款幂等跳过 refundNo={}", result.refundNo());
                return;
            }

            if (result.success()) {
                refund.markSuccess(result.wxRefundId());
                refundRepository.save(refund);
                // 复用既有落地逻辑：订单 →REFUND + 联动取消返利（RebateService.cancelForRefund）。
                int cancelled = refundService.handleRefundSucceeded(refund.getOrderId());
                if (cancelled > 0) {
                    log.info("退款成功后已联动取消 {} 条推荐返利 orderId={}", cancelled, refund.getOrderId());
                }
            } else {
                refund.markFailed();
                refundRepository.save(refund);
                orderRepository.findById(refund.getOrderId()).ifPresent(order -> {
                    if (order.getStatus() == OrderStatus.REFUNDING) {
                        order.setStatus(OrderStatus.PAID);
                        orderRepository.save(order);
                    }
                });
            }
        });
    }

    private RefundResultDto toDto(Refund refund) {
        String refundedAt = refund.getSucceededAt() != null
                ? refund.getSucceededAt().atZone(ZoneId.systemDefault()).format(ISO_FMT) : null;
        return new RefundResultDto(
                refund.getRefundNo(),
                refund.getStatus().name().toLowerCase(),
                refund.getAmountCents(),
                refundedAt);
    }
}
