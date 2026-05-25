package com.sanshuiyuan.h5.wxmsg.service;

import com.sanshuiyuan.h5.wxmsg.domain.WxMessageLog;
import com.sanshuiyuan.h5.wxmsg.domain.WxMsgStatus;
import com.sanshuiyuan.h5.wxmsg.domain.WxMsgType;
import com.sanshuiyuan.h5.wxmsg.repository.WxMessageLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/**
 * 模板 ID 未配置时的降级实现。记录 SKIPPED_NO_TEMPLATE_ID，不抛异常，不影响主流程。
 */
@Service
@ConditionalOnMissingBean(name = "realWxTemplateMsgService")
public class NoopWxTemplateMsgService implements WxTemplateMsgService {

    private static final Logger log = LoggerFactory.getLogger(NoopWxTemplateMsgService.class);

    private final WxMessageLogRepository logRepository;

    public NoopWxTemplateMsgService(WxMessageLogRepository logRepository) {
        this.logRepository = logRepository;
    }

    @Override
    public void sendPaySuccess(String openid, long orderId, String orderNo,
                               String modelName, long paidAmountCents, String cooldownEndAt) {
        log.debug("[wxmsg] PAY_SUCCESS 推送跳过（模板 ID 未配置），orderId={}", orderId);
        saveSkipped(openid, WxMsgType.PAY_SUCCESS, orderId);
    }

    @Override
    public void sendRefundSuccess(String openid, long orderId, String orderNo,
                                  long refundAmountCents) {
        log.debug("[wxmsg] REFUND_SUCCESS 推送跳过（模板 ID 未配置），orderId={}", orderId);
        saveSkipped(openid, WxMsgType.REFUND_SUCCESS, orderId);
    }

    private void saveSkipped(String openid, WxMsgType type, long orderId) {
        try {
            boolean alreadyLogged = logRepository.existsByMsgTypeAndOrderIdAndStatus(
                    type, orderId, WxMsgStatus.SKIPPED_NO_TEMPLATE_ID);
            if (!alreadyLogged) {
                logRepository.save(WxMessageLog.skipped(
                        openid, type, orderId, "", WxMsgStatus.SKIPPED_NO_TEMPLATE_ID));
            }
        } catch (Exception e) {
            log.warn("[wxmsg] 写 SKIPPED 日志失败，忽略", e);
        }
    }
}
