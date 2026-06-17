package com.sanshuiyuan.cend.wxmsg.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanshuiyuan.cend.wxmsg.domain.WxMessageLog;
import com.sanshuiyuan.cend.wxmsg.domain.WxMsgStatus;
import com.sanshuiyuan.cend.wxmsg.domain.WxMsgType;
import com.sanshuiyuan.cend.wxmsg.infra.WxSubscribeChecker;
import com.sanshuiyuan.cend.wxmsg.repository.WxMessageLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 小程序订阅消息（subscribe message）推送实现，面向 {@code users.channel='WECHAT_MP'} 的小程序用户。
 *
 * <p>与公众号模板消息（{@link RealWxTemplateMsgService}，走 {@code message/template/send}）的区别：
 * 小程序订阅消息走 {@code message/subscribe/send}，且 access_token 取自小程序 appId/appSecret
 * （与 {@code HttpWxMiniPhoneClient}/{@code HttpWxMiniCodeClient} 同凭证 {@code wx.miniprogram.*}），
 * 因此 token 由本类自管缓存（提前 5 分钟过期），不复用公众号的 {@code WxAccessTokenService}。
 *
 * <p>激活条件 {@code wx.miniprogram.app-secret} 已配置；secret 为空（桩模式）时 token 取不到，
 * doSend 写 FAILED 日志降级，绝不抛出影响 SLA 任务。
 */
@Service
@ConditionalOnProperty(name = "wx.miniprogram.app-secret", matchIfMissing = false)
public class WxMiniSubscribeMsgService {

    private static final Logger log = LoggerFactory.getLogger(WxMiniSubscribeMsgService.class);
    private static final String TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token";
    private static final String SEND_URL =
            "https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token=";

    private final String appId;
    private final String appSecret;
    private final WxSubscribeChecker subscribeChecker;
    private final WxMessageLogRepository logRepository;
    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${wx.tpl.mini.claim-confirm-remind:}")
    private String claimConfirmRemindTemplateId;

    private volatile String cachedAccessToken;
    private volatile long tokenExpiresAt;

    public WxMiniSubscribeMsgService(@Value("${wx.miniprogram.app-id:stub}") String appId,
                                     @Value("${wx.miniprogram.app-secret:}") String appSecret,
                                     WxSubscribeChecker subscribeChecker,
                                     WxMessageLogRepository logRepository) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.subscribeChecker = subscribeChecker;
        this.logRepository = logRepository;
    }

    /**
     * 接单确认提醒（matching-service S2S 调用，channel=WECHAT_MP 时走此通道）。
     * requestId 复用 order_id 列存放需求单 id。SOFT/FINAL 两阶段各发一次，不做 SENT 幂等跳过
     * —— 与公众号 {@link RealWxTemplateMsgService#sendClaimConfirmRemind} 一致，重发频率由 matching SLA 窗口兜底。
     */
    public void sendClaimConfirmRemind(String openid, long requestId,
                                       String stageLabel, String deadlineDisplay) {
        if (claimConfirmRemindTemplateId == null || claimConfirmRemindTemplateId.isBlank()) {
            log.debug("[wxmsg] mini CLAIM_CONFIRM_REMIND 跳过（模板 ID 未配置），requestId={}", requestId);
            saveSkipped(openid, requestId, "", WxMsgStatus.SKIPPED_NO_TEMPLATE_ID);
            return;
        }

        // 订阅消息无需关注校验（小程序订阅授权独立于公众号关注），仅复用 subscribeChecker 的桩兼容性即可，
        // 此处直接发送，由微信侧 errcode 反馈授权失效（43101 等）。

        // 订阅消息 data：key 名称取决于小程序后台模板字段（thing/number/time），与公众号 first/keyword 不同。
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("thing1", item("接单需求待确认"));
        data.put("number2", item(String.valueOf(requestId)));
        data.put("thing3", item(stageLabel));         // 温馨提醒（12小时）/ 最后提醒（即将自动释放）
        data.put("time4", item(deadlineDisplay));     // 请在 12 小时内确认 / 请立即确认
        data.put("thing5", item("逾期将自动释放需求"));

        String page = "pages/matching/confirm/index?id=" + requestId;
        doSend(openid, claimConfirmRemindTemplateId, page, data, requestId);
    }

    private void doSend(String openid, String templateId, String page,
                        Map<String, Object> data, long requestId) {
        try {
            String token = getAccessToken(false);
            if (token == null || token.isBlank()) {
                log.warn("[wxmsg] mini access_token 为空，跳过推送 requestId={}", requestId);
                saveLog(WxMessageLog.failed(openid, WxMsgType.CLAIM_CONFIRM_REMIND, requestId,
                        templateId, "access_token empty"));
                return;
            }

            String responseJson = postSubscribe(token, openid, templateId, page, data);
            JsonNode node = objectMapper.readTree(responseJson);
            int errCode = node.path("errcode").asInt(-1);
            if (errCode == 40001) {
                // token 过期：强刷一次重试
                log.info("[wxmsg] mini subscribe token 过期，刷新重试 requestId={}", requestId);
                responseJson = postSubscribe(getAccessToken(true), openid, templateId, page, data);
                node = objectMapper.readTree(responseJson);
                errCode = node.path("errcode").asInt(-1);
            }

            if (errCode == 0) {
                String wxMsgId = node.path("msgid").asText("");
                saveLog(WxMessageLog.sent(openid, WxMsgType.CLAIM_CONFIRM_REMIND, requestId, templateId, wxMsgId));
                log.info("[wxmsg] mini CLAIM_CONFIRM_REMIND 推送成功 requestId={}", requestId);
            } else {
                String errmsg = node.path("errmsg").asText("");
                log.warn("[wxmsg] mini CLAIM_CONFIRM_REMIND 推送失败 requestId={} errcode={} errmsg={}",
                        requestId, errCode, errmsg);
                saveLog(WxMessageLog.failed(openid, WxMsgType.CLAIM_CONFIRM_REMIND, requestId, templateId,
                        "errcode=" + errCode + " " + errmsg));
            }
        } catch (Exception e) {
            log.error("[wxmsg] mini CLAIM_CONFIRM_REMIND 推送异常 requestId={}", requestId, e);
            saveLog(WxMessageLog.failed(openid, WxMsgType.CLAIM_CONFIRM_REMIND, requestId, templateId, e.getMessage()));
        }
    }

    private String postSubscribe(String token, String openid, String templateId,
                                 String page, Map<String, Object> data) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("touser", openid);
        body.put("template_id", templateId);
        body.put("page", page);
        body.put("data", data);
        body.put("miniprogram_state", "formal");

        String bodyJson = objectMapper.writeValueAsString(body);
        return restClient.post()
                .uri(SEND_URL + token)
                .header("Content-Type", "application/json;charset=UTF-8")
                .body(bodyJson)
                .retrieve()
                .body(String.class);
    }

    /**
     * 小程序 access_token：volatile 缓存 + synchronized 单点刷新，提前 5 分钟过期防临界失效
     * （与 {@link com.sanshuiyuan.cend.identity.HttpWxMiniPhoneClient#getAccessToken} 同模式）。
     */
    private synchronized String getAccessToken(boolean forceRefresh) {
        long now = System.currentTimeMillis();
        if (!forceRefresh && cachedAccessToken != null && now < tokenExpiresAt) {
            return cachedAccessToken;
        }
        if (appSecret == null || appSecret.isBlank() || "stub".equals(appId)) {
            log.debug("[wxmsg] mini app-secret 未配置，跳过 access_token 刷新（stub 模式）");
            return "";
        }
        String raw = restClient.get()
                .uri(TOKEN_URL + "?grant_type=client_credential&appid={appid}&secret={secret}", appId, appSecret)
                .retrieve()
                .body(String.class);
        JsonNode body;
        try {
            body = objectMapper.readTree(raw);
        } catch (Exception e) {
            log.warn("[wxmsg] mini token 响应解析失败");
            return "";
        }
        String token = body.path("access_token").asText(null);
        if (token == null || token.isBlank()) {
            log.warn("[wxmsg] mini token 获取失败 errcode={} errmsg={}",
                    body.path("errcode").asInt(), body.path("errmsg").asText());
            return "";
        }
        long expiresIn = body.path("expires_in").asLong(7200);
        this.tokenExpiresAt = System.currentTimeMillis() + (expiresIn - 300) * 1000L;
        this.cachedAccessToken = token;
        return token;
    }

    private void saveLog(WxMessageLog logEntry) {
        try {
            logRepository.save(logEntry);
        } catch (Exception e) {
            log.warn("[wxmsg] mini 写推送日志失败，忽略", e);
        }
    }

    private void saveSkipped(String openid, long requestId, String templateId, WxMsgStatus reason) {
        try {
            if (!logRepository.existsByMsgTypeAndOrderIdAndStatus(WxMsgType.CLAIM_CONFIRM_REMIND, requestId, reason)) {
                logRepository.save(WxMessageLog.skipped(openid, WxMsgType.CLAIM_CONFIRM_REMIND, requestId, templateId, reason));
            }
        } catch (Exception e) {
            log.warn("[wxmsg] mini 写 SKIPPED 日志失败，忽略", e);
        }
    }

    private static Map<String, String> item(String value) {
        return Map.of("value", value);
    }
}
