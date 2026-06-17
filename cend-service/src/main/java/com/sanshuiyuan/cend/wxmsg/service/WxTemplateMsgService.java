package com.sanshuiyuan.cend.wxmsg.service;

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

    /**
     * 接单确认提醒（matching-service S2S 调用）。requestId 复用 order_id 列存放需求单 id。
     * SOFT/FINAL 两阶段各发一次，不做幂等跳过（重发频率由 matching SLA 窗口控制）。
     */
    void sendClaimConfirmRemind(String openid, long requestId, String stageLabel, String deadlineDisplay);
}
