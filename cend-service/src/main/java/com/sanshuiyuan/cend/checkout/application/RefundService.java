package com.sanshuiyuan.cend.checkout.application;

import com.sanshuiyuan.cend.checkout.api.dto.RefundResultDto;
import com.sanshuiyuan.cend.checkout.domain.CendOrder;
import com.sanshuiyuan.cend.checkout.domain.OrderStatus;
import com.sanshuiyuan.cend.checkout.domain.Refund;
import com.sanshuiyuan.cend.checkout.domain.RefundStatus;
import com.sanshuiyuan.cend.checkout.infra.repository.CendOrderRepository;
import com.sanshuiyuan.cend.checkout.infra.repository.RefundRepository;
import com.sanshuiyuan.cend.checkout.infra.wxpay.WxRefundClient;
import com.sanshuiyuan.cend.common.BizException;
import com.sanshuiyuan.cend.common.ErrorCode;
import com.sanshuiyuan.cend.rebate.application.RebateService;
import com.sanshuiyuan.cend.realtime.H5RealtimeBroadcaster;
import com.sanshuiyuan.cend.realtime.H5RealtimeEvent;
import com.sanshuiyuan.cend.wxmsg.event.RefundSucceededEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);
    private static final DateTimeFormatter ISO_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final CendOrderRepository orderRepo;
    private final RefundRepository refundRepo;
    private final WxRefundClient wxRefundClient;
    private final ApplicationEventPublisher eventPublisher;
    private final RebateService rebateService;
    private final H5RealtimeBroadcaster realtimeBroadcaster;
    private final AdminOrderProjector adminOrderProjector;

    public RefundService(CendOrderRepository orderRepo,
                         RefundRepository refundRepo,
                         WxRefundClient wxRefundClient,
                         ApplicationEventPublisher eventPublisher,
                         RebateService rebateService,
                         H5RealtimeBroadcaster realtimeBroadcaster,
                         AdminOrderProjector adminOrderProjector) {
        this.orderRepo = orderRepo;
        this.refundRepo = refundRepo;
        this.wxRefundClient = wxRefundClient;
        this.eventPublisher = eventPublisher;
        this.rebateService = rebateService;
        this.realtimeBroadcaster = realtimeBroadcaster;
        this.adminOrderProjector = adminOrderProjector;
    }

    @Transactional
    public RefundResultDto requestRefund(Long orderId, String openid) {
        CendOrder order = orderRepo.findById(orderId)
                .orElseThrow(() -> new BizException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.getOpenid().equals(openid)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }

        if (order.getStatus() != OrderStatus.PAID) {
            throw new BizException(ErrorCode.ORDER_NOT_REFUNDABLE);
        }

        LocalDateTime cooldownEnd = order.getCooldownEndAt();
        if (cooldownEnd != null && LocalDateTime.now().isAfter(cooldownEnd)) {
            throw new BizException(ErrorCode.COOLDOWN_EXPIRED);
        }

        // Idempotent: if an existing PROCESSING or SUCCESS refund exists, return it
        Refund existing = refundRepo.findByOrderId(orderId).orElse(null);
        if (existing != null) {
            String refundedAt = existing.getSucceededAt() != null
                    ? existing.getSucceededAt().atZone(ZoneId.systemDefault()).format(ISO_FMT) : null;
            return new RefundResultDto(
                    existing.getRefundNo(),
                    existing.getStatus().name().toLowerCase(),
                    existing.getAmountCents(),
                    refundedAt
            );
        }

        String refundNo = "RF" + System.currentTimeMillis() + orderId;
        Refund refund = Refund.create(orderId, refundNo, order.getAmountCents());

        try {
            refundRepo.save(refund);
        } catch (DataIntegrityViolationException e) {
            // Concurrent insert — unique constraint on order_id
            log.warn("并发退款请求 orderId={}", orderId);
            return refundRepo.findByOrderId(orderId)
                    .map(r -> new RefundResultDto(
                            r.getRefundNo(), r.getStatus().name().toLowerCase(),
                            r.getAmountCents(),
                            r.getSucceededAt() != null ? r.getSucceededAt().atZone(ZoneId.systemDefault()).format(ISO_FMT) : null))
                    .orElseThrow(() -> new BizException(ErrorCode.INTERNAL_ERROR));
        }

        order.markRefunding();
        orderRepo.save(order);
        // 双写：投影退款中状态（REFUNDING）到 admin orders 表。
        adminOrderProjector.project(order);

        // Call WxRefundClient outside the transaction boundary would be ideal,
        // but for simplicity in the stub phase, we do it here.
        try {
            wxRefundClient.refund(order.getOrderNo(), refundNo, order.getAmountCents());
        } catch (Exception e) {
            log.error("微信退款调用失败 orderId={}", orderId, e);
            refund.markFailed();
            order.revertToPaid();
            refundRepo.save(refund);
            orderRepo.save(order);
            // 双写：退款发起失败回退为已支付（PAID），同步到 admin orders 表。
            adminOrderProjector.project(order);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "退款发起失败，请稍后重试");
        }

        return new RefundResultDto(refundNo, refund.getStatus().name().toLowerCase(),
                refund.getAmountCents(), null);
    }

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
     * 应用微信侧的退款结果。回调与主动查单兜底（{@link ReconcileRefundingOrdersJob}）共享此入口，
     * 以幂等的方式更新 refund / order / 双写投影 / 返利取消 / 模板消息 / 实时广播。
     *
     * <p>幂等：refund 已是 SUCCESS 时直接返回。
     */
    @Transactional
    public void applyRefundResult(WxRefundClient.RefundCallbackResult result) {
        refundRepo.findByRefundNo(result.refundNo()).ifPresent(refund -> {
            if (refund.getStatus() == RefundStatus.SUCCESS) {
                log.info("退款幂等跳过 refundNo={}", result.refundNo());
                return;
            }

            if (result.success()) {
                refund.markSuccess(result.wxRefundId());
                refundRepo.save(refund);

                orderRepo.findById(refund.getOrderId()).ifPresent(order -> {
                    order.markRefunded();
                    orderRepo.save(order);
                    // 双写：投影退款成功状态（REFUNDED）到 admin orders 表。
                    adminOrderProjector.project(order);
                    // 011: 退款成功（合同解除）→ 取消该订单全部返利。
                    // 冷静期内取消 FROZEN(REFUND_COOLDOWN)，冷静期后取消 CONFIRMED(REFUND_POST_COOLDOWN)。
                    int cancelledRebates = rebateService.cancelForRefund(order.getId());
                    // Spec 106: 发布退款成功事件，事务提交后推送模板消息
                    eventPublisher.publishEvent(new RefundSucceededEvent(
                            order.getId(), order.getOpenid(), order.getOrderNo(),
                            refund.getAmountCents()));
                    realtimeBroadcaster.publish(order.getOpenid(),
                            H5RealtimeEvent.order(order.getId(), order.getStatus().name(), "refund_success"));
                    if (cancelledRebates > 0) {
                        log.info("退款成功后已取消 {} 条返利（由受益人通道推送状态变更）", cancelledRebates);
                    }
                });
            } else {
                refund.markFailed();
                refundRepo.save(refund);

                orderRepo.findById(refund.getOrderId()).ifPresent(order -> {
                    order.revertToPaid();
                    orderRepo.save(order);
                    // 双写：退款失败回退为已支付（PAID），同步到 admin orders 表。
                    adminOrderProjector.project(order);
                });
            }
        });
    }
}
