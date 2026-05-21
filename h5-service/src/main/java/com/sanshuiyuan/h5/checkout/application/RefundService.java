package com.sanshuiyuan.h5.checkout.application;

import com.sanshuiyuan.h5.checkout.api.dto.RefundResultDto;
import com.sanshuiyuan.h5.checkout.domain.H5Order;
import com.sanshuiyuan.h5.checkout.domain.OrderStatus;
import com.sanshuiyuan.h5.checkout.domain.Refund;
import com.sanshuiyuan.h5.checkout.domain.RefundStatus;
import com.sanshuiyuan.h5.checkout.infra.repository.H5OrderRepository;
import com.sanshuiyuan.h5.checkout.infra.repository.RefundRepository;
import com.sanshuiyuan.h5.checkout.infra.wxpay.WxRefundClient;
import com.sanshuiyuan.h5.common.BizException;
import com.sanshuiyuan.h5.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final H5OrderRepository orderRepo;
    private final RefundRepository refundRepo;
    private final WxRefundClient wxRefundClient;

    public RefundService(H5OrderRepository orderRepo,
                         RefundRepository refundRepo,
                         WxRefundClient wxRefundClient) {
        this.orderRepo = orderRepo;
        this.refundRepo = refundRepo;
        this.wxRefundClient = wxRefundClient;
    }

    @Transactional
    public RefundResultDto requestRefund(Long orderId, String openid) {
        H5Order order = orderRepo.findById(orderId)
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
            throw new BizException(ErrorCode.INTERNAL_ERROR, "退款发起失败，请稍后重试");
        }

        return new RefundResultDto(refundNo, refund.getStatus().name().toLowerCase(),
                refund.getAmountCents(), null);
    }

    @Transactional
    public void handleRefundCallback(String body, String signature,
                                      String timestamp, String nonce, String serial) {
        log.info("收到微信退款回调");
        // TODO: verify V3 signature + decrypt once real wxpay is wired

        // Stub: parse refund_no from body or use a simple protocol
        // In production, decrypt the callback body to extract refund info
        WxRefundClient.RefundCallbackResult result =
                wxRefundClient.parseCallback(body, signature, timestamp, nonce, serial);

        if (result == null) {
            log.warn("退款回调解析失败");
            return;
        }

        refundRepo.findByRefundNo(result.refundNo()).ifPresent(refund -> {
            if (refund.getStatus() == RefundStatus.SUCCESS) {
                log.info("退款回调幂等跳过 refundNo={}", result.refundNo());
                return;
            }

            if (result.success()) {
                refund.markSuccess(result.wxRefundId());
                refundRepo.save(refund);

                orderRepo.findById(refund.getOrderId()).ifPresent(order -> {
                    order.markRefunded();
                    orderRepo.save(order);
                });
            } else {
                refund.markFailed();
                refundRepo.save(refund);

                orderRepo.findById(refund.getOrderId()).ifPresent(order -> {
                    order.revertToPaid();
                    orderRepo.save(order);
                });
            }
        });
    }
}
