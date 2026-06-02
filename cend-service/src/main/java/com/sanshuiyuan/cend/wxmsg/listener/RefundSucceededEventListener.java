package com.sanshuiyuan.cend.wxmsg.listener;

import com.sanshuiyuan.cend.wxmsg.event.RefundSucceededEvent;
import com.sanshuiyuan.cend.wxmsg.service.WxTemplateMsgService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 监听 RefundSucceededEvent，在退款事务提交后异步推送「退款成功」模板消息。
 */
@Component
public class RefundSucceededEventListener {

    private static final Logger log = LoggerFactory.getLogger(RefundSucceededEventListener.class);

    private final WxTemplateMsgService msgService;

    public RefundSucceededEventListener(WxTemplateMsgService msgService) {
        this.msgService = msgService;
    }

    @Async("wxMsgExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRefundSucceeded(RefundSucceededEvent event) {
        try {
            msgService.sendRefundSuccess(
                    event.getOpenid(),
                    event.getOrderId(),
                    event.getOrderNo(),
                    event.getRefundAmountCents()
            );
        } catch (Exception e) {
            log.error("[wxmsg] RefundSucceededEventListener 异常 orderId={}", event.getOrderId(), e);
        }
    }
}
