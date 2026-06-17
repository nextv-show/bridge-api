package com.sanshuiyuan.cend.wxmsg.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sanshuiyuan.cend.wxmsg.service.WxMiniSubscribeMsgService;
import com.sanshuiyuan.cend.wxmsg.service.WxTemplateMsgService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 内部微信消息端点（service-to-service，S2S token 鉴权，不对外暴露）。
 * matching-service 的 WxMsgClaimConfirmNotifier 在接单确认 SLA 提醒节点调用。
 * 复用 cend 已有的微信消息基础设施（access_token / 关注校验 / 推送日志）。
 *
 * <p>按 {@code channel} 路由通道：{@code WECHAT_MP}（小程序用户）走小程序订阅消息
 * {@link WxMiniSubscribeMsgService}，其余（含空值、{@code WECHAT_H5}）走公众号模板消息
 * {@link WxTemplateMsgService}。小程序服务以 {@link ObjectProvider} 注入，未激活
 * （{@code wx.miniprogram.app-secret} 未配置）时回退公众号通道并告警。
 */
@RestController
@RequestMapping("/internal/wxmsg")
public class WxMsgInternalController {

    private static final Logger log = LoggerFactory.getLogger(WxMsgInternalController.class);
    private static final String CHANNEL_MINIAPP = "WECHAT_MP";

    private final WxTemplateMsgService msgService;
    private final ObjectProvider<WxMiniSubscribeMsgService> miniSubscribeMsgService;

    public WxMsgInternalController(WxTemplateMsgService msgService,
                                   ObjectProvider<WxMiniSubscribeMsgService> miniSubscribeMsgService) {
        this.msgService = msgService;
        this.miniSubscribeMsgService = miniSubscribeMsgService;
    }

    @PostMapping("/claim-reminder")
    public Map<String, Object> claimReminder(@RequestBody ClaimReminderBody body) {
        WxMiniSubscribeMsgService mini = miniSubscribeMsgService.getIfAvailable();
        if (CHANNEL_MINIAPP.equals(body.channel()) && mini != null) {
            mini.sendClaimConfirmRemind(
                    body.openid(), body.requestId(), body.stageLabel(), body.deadlineDisplay());
        } else {
            if (CHANNEL_MINIAPP.equals(body.channel())) {
                log.warn("[wxmsg] channel=WECHAT_MP 但小程序订阅消息服务未激活，回退公众号模板消息 requestId={}",
                        body.requestId());
            }
            msgService.sendClaimConfirmRemind(
                    body.openid(), body.requestId(), body.stageLabel(), body.deadlineDisplay());
        }
        return Map.of("status", "ok");
    }

    // 线格式为 snake_case（matching WxMsgClaimConfirmNotifier 发送）；cend 无全局 SNAKE_CASE 策略，
    // 故显式 @JsonProperty 映射。stage（SOFT/FINAL）仅作审计/扩展用，文案由 matching 侧组装好下发。
    // channel（WECHAT_MP/WECHAT_H5）决定走小程序订阅消息还是公众号模板消息。
    public record ClaimReminderBody(
            @JsonProperty("openid") String openid,
            @JsonProperty("request_id") long requestId,
            @JsonProperty("stage") String stage,
            @JsonProperty("stage_label") String stageLabel,
            @JsonProperty("deadline_display") String deadlineDisplay,
            @JsonProperty("channel") String channel) {}
}
