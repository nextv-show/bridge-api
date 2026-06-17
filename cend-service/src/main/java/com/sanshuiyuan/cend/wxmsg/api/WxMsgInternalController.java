package com.sanshuiyuan.cend.wxmsg.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sanshuiyuan.cend.wxmsg.service.WxTemplateMsgService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 内部微信模板消息端点（service-to-service，S2S token 鉴权，不对外暴露）。
 * matching-service 的 WxMsgClaimConfirmNotifier 在接单确认 SLA 提醒节点调用。
 * 复用 cend 已有的微信模板消息基础设施（access_token / 关注校验 / 推送日志）。
 */
@RestController
@RequestMapping("/internal/wxmsg")
public class WxMsgInternalController {

    private final WxTemplateMsgService msgService;

    public WxMsgInternalController(WxTemplateMsgService msgService) {
        this.msgService = msgService;
    }

    @PostMapping("/claim-reminder")
    public Map<String, Object> claimReminder(@RequestBody ClaimReminderBody body) {
        msgService.sendClaimConfirmRemind(
                body.openid(), body.requestId(), body.stageLabel(), body.deadlineDisplay());
        return Map.of("status", "ok");
    }

    // 线格式为 snake_case（matching WxMsgClaimConfirmNotifier 发送）；cend 无全局 SNAKE_CASE 策略，
    // 故显式 @JsonProperty 映射。stage（SOFT/FINAL）仅作审计/扩展用，文案由 matching 侧组装好下发。
    public record ClaimReminderBody(
            @JsonProperty("openid") String openid,
            @JsonProperty("request_id") long requestId,
            @JsonProperty("stage") String stage,
            @JsonProperty("stage_label") String stageLabel,
            @JsonProperty("deadline_display") String deadlineDisplay) {}
}
