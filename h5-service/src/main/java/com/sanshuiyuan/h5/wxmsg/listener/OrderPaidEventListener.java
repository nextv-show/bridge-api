package com.sanshuiyuan.h5.wxmsg.listener;

import com.sanshuiyuan.h5.wxmsg.event.OrderPaidEvent;
import com.sanshuiyuan.h5.wxmsg.service.WxTemplateMsgService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 监听 OrderPaidEvent，在支付事务提交后异步推送「支付成功」模板消息。
 * 推送失败仅记录日志，不影响主业务。
 */
@Component
public class OrderPaidEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderPaidEventListener.class);

    private final WxTemplateMsgService msgService;

    public OrderPaidEventListener(WxTemplateMsgService msgService) {
        this.msgService = msgService;
    }

    @Async("wxMsgExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPaid(OrderPaidEvent event) {
        try {
            msgService.sendPaySuccess(
                    event.getOpenid(),
                    event.getOrderId(),
                    event.getOrderNo(),
                    event.getModelName(),
                    event.getPaidAmountCents(),
                    event.getCooldownEndAt()
            );
        } catch (Exception e) {
            log.error("[wxmsg] OrderPaidEventListener 异常 orderId={}", event.getOrderId(), e);
        }
    }
}
