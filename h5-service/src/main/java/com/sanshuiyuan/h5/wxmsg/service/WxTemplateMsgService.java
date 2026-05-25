package com.sanshuiyuan.h5.wxmsg.service;

/**
 * 微信模板消息推送接缝。
 * 真实实现：RealWxTemplateMsgService（templateId 已配置时激活）。
 * 降级实现：NoopWxTemplateMsgService（未配置时写 SKIPPED_NO_TEMPLATE_ID 日志）。
 */
public interface WxTemplateMsgService {

    void sendPaySuccess(String openid, long orderId, String orderNo,
                        String modelName, long paidAmountCents, String cooldownEndAt);

    void sendRefundSuccess(String openid, long orderId, String orderNo,
                           long refundAmountCents);
}
