package com.sanshuiyuan.cend.checkout.application;

import com.sanshuiyuan.cend.checkout.domain.CendOrder;
import com.sanshuiyuan.cend.checkout.domain.OrderStatus;
import com.sanshuiyuan.cend.checkout.domain.PaymentInbox;
import com.sanshuiyuan.cend.checkout.infra.repository.DeviceSpecRepository;
import com.sanshuiyuan.cend.checkout.infra.repository.CendOrderRepository;
import com.sanshuiyuan.cend.checkout.infra.repository.PaymentInboxRepository;
import com.sanshuiyuan.cend.rebate.application.RebateService;
import com.sanshuiyuan.cend.wxmsg.event.OrderPaidEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 支付成功后的统一落账副作用，供异步回调（{@link PayCallbackUseCase}）与
 * 主动查单对账任务（ReconcilePendingOrdersJob）共享，保证两条路径行为一致且幂等。
 *
 * <p>幂等以 payment_inbox.transaction_id 唯一约束为锚点：同一 transactionId 仅处理一次。
 */
@Service
public class OrderPaymentCompletionService {

    private static final Logger log = LoggerFactory.getLogger(OrderPaymentCompletionService.class);
    private static final DateTimeFormatter ISO_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final PaymentInboxRepository inboxRepo;
    private final CendOrderRepository orderRepo;
    private final DeviceSpecRepository specRepo;
    private final ApplicationEventPublisher eventPublisher;
    private final RebateService rebateService;
    private final AdminOrderProjector adminOrderProjector;

    public OrderPaymentCompletionService(PaymentInboxRepository inboxRepo, CendOrderRepository orderRepo,
                                         DeviceSpecRepository specRepo, ApplicationEventPublisher eventPublisher,
                                         RebateService rebateService, AdminOrderProjector adminOrderProjector) {
        this.inboxRepo = inboxRepo;
        this.orderRepo = orderRepo;
        this.specRepo = specRepo;
        this.eventPublisher = eventPublisher;
        this.rebateService = rebateService;
        this.adminOrderProjector = adminOrderProjector;
    }

    /**
     * 将订单置为已支付并执行全部落账副作用（双写 admin 投影、冻结返利、发布支付成功事件）。
     * 幂等：transactionId 已处理过或订单已非 PENDING_PAY 时直接返回。
     */
    @Transactional
    public void completePaid(CendOrder order, String transactionId, String rawBodyForInbox) {
        // 1) 幂等锚点：payment_inbox 唯一键插入，重复即视为已处理。
        try {
            inboxRepo.save(PaymentInbox.create(transactionId, order.getOrderNo(), rawBodyForInbox));
        } catch (DataIntegrityViolationException e) {
            log.info("Duplicate payment transactionId={} orderNo={} — idempotent skip",
                    transactionId, order.getOrderNo());
            return;
        }

        // 2) 二次校验订单状态，避免并发下重复落账。
        if (order.getStatus() != OrderStatus.PENDING_PAY) {
            log.info("Order {} already processed (status={}) — skip completion",
                    order.getOrderNo(), order.getStatus());
            return;
        }

        // 3) 置为已支付（占位 SN + 24h 冷却）。
        String placeholderSn = "SN-PENDING-" + order.getOrderNo();
        LocalDateTime cooldownEnd = LocalDateTime.now().plusHours(24);
        order.markPaid(transactionId, placeholderSn, cooldownEnd);
        orderRepo.save(order);

        // 4) 双写：同事务内投影已支付状态到 admin orders 表。
        adminOrderProjector.project(order);

        // 5) 011: 按订单快照的 L1/L2 受益人冻结返利（FROZEN）。仅 L1/L2，绝不递归 L3+。
        rebateService.freezeForOrder(order.getId(), order.getInviterId(), order.getGrandInviterId());

        // 6) Spec 106: 发布支付成功事件，事务提交后由 OrderPaidEventListener 异步推送模板消息。
        String modelName = specRepo.findByModelCode(order.getModelCode())
                .map(s -> s.getName())
                .orElse(order.getModelCode());
        String cooldownEndAtStr = cooldownEnd.atZone(ZoneId.systemDefault()).format(ISO_FMT);
        eventPublisher.publishEvent(new OrderPaidEvent(
                order.getId(), order.getOpenid(), order.getOrderNo(),
                modelName, order.getAmountCents(), cooldownEndAtStr));
    }
}
