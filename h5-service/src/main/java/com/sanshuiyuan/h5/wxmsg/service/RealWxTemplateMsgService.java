package com.sanshuiyuan.h5.wxmsg.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanshuiyuan.h5.wxmsg.config.WxMsgProperties;
import com.sanshuiyuan.h5.wxmsg.domain.WxMessageLog;
import com.sanshuiyuan.h5.wxmsg.domain.WxMsgStatus;
import com.sanshuiyuan.h5.wxmsg.domain.WxMsgType;
import com.sanshuiyuan.h5.wxmsg.infra.WxAccessTokenService;
import com.sanshuiyuan.h5.wxmsg.infra.WxSubscribeChecker;
import com.sanshuiyuan.h5.wxmsg.repository.WxMessageLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 真实微信模板消息推送实现，在至少一个 templateId 已配置时激活。
 */
@Service("realWxTemplateMsgService")
@ConditionalOnProperty(name = "wx.tpl.pay-success", matchIfMissing = false)
public class RealWxTemplateMsgService implements WxTemplateMsgService {

    private static final Logger log = LoggerFactory.getLogger(RealWxTemplateMsgService.class);
    private static final String SEND_URL =
            "https://api.weixin.qq.com/cgi-bin/message/template/send?access_token=";
    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final WxMsgProperties props;
    private final WxAccessTokenService tokenService;
    private final WxSubscribeChecker subscribeChecker;
    private final WxMessageLogRepository logRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${h5.public-base-url:http://localhost:5173}")
    private String publicBaseUrl;

    public RealWxTemplateMsgService(WxMsgProperties props,
                                    WxAccessTokenService tokenService,
                                    WxSubscribeChecker subscribeChecker,
                                    WxMessageLogRepository logRepository) {
        this.props = props;
        this.tokenService = tokenService;
        this.subscribeChecker = subscribeChecker;
        this.logRepository = logRepository;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void sendPaySuccess(String openid, long orderId, String orderNo,
                               String modelName, long paidAmountCents, String cooldownEndAt) {
        String templateId = props.getPaySuccess();
        if (!props.isPaySuccessConfigured()) {
            saveSkipped(openid, WxMsgType.PAY_SUCCESS, orderId, "", WxMsgStatus.SKIPPED_NO_TEMPLATE_ID);
            return;
        }
        if (!subscribeChecker.isSubscribed(openid)) {
            saveSkipped(openid, WxMsgType.PAY_SUCCESS, orderId, templateId, WxMsgStatus.SKIPPED_UNSUBSCRIBED);
            return;
        }

        // 幂等：已成功推送则跳过
        if (logRepository.existsByMsgTypeAndOrderIdAndStatus(WxMsgType.PAY_SUCCESS, orderId, WxMsgStatus.SENT)) {
            log.debug("[wxmsg] PAY_SUCCESS 已推送，orderId={}", orderId);
            return;
        }

        String amount = String.format("¥%.2f", paidAmountCents / 100.0);
        String cooldownDisplay = formatIso(cooldownEndAt);

        Map<String, Object> data = new HashMap<>();
        data.put("first",    item("您的三水元水机已购置成功！"));
        data.put("keyword1", item(orderNo));
        data.put("keyword2", item(modelName));
        data.put("keyword3", item(amount));
        data.put("keyword4", item(cooldownDisplay));
        data.put("remark",   item("24 小时内可无理由退款，点击查看订单详情"));

        String url = publicBaseUrl + "/success/" + orderId;
        doSend(openid, templateId, url, data, WxMsgType.PAY_SUCCESS, orderId);
    }

    @Override
    public void sendRefundSuccess(String openid, long orderId, String orderNo,
                                  long refundAmountCents) {
        String templateId = props.getRefundSuccess();
        if (!props.isRefundSuccessConfigured()) {
            saveSkipped(openid, WxMsgType.REFUND_SUCCESS, orderId, "", WxMsgStatus.SKIPPED_NO_TEMPLATE_ID);
            return;
        }
        if (!subscribeChecker.isSubscribed(openid)) {
            saveSkipped(openid, WxMsgType.REFUND_SUCCESS, orderId, templateId, WxMsgStatus.SKIPPED_UNSUBSCRIBED);
            return;
        }

        if (logRepository.existsByMsgTypeAndOrderIdAndStatus(WxMsgType.REFUND_SUCCESS, orderId, WxMsgStatus.SENT)) {
            log.debug("[wxmsg] REFUND_SUCCESS 已推送，orderId={}", orderId);
            return;
        }

        String amount = String.format("¥%.2f", refundAmountCents / 100.0);

        Map<String, Object> data = new HashMap<>();
        data.put("first",    item("您的退款已原路退回，请注意查收"));
        data.put("keyword1", item(orderNo));
        data.put("keyword2", item(amount));
        data.put("keyword3", item("微信原路退回"));
        data.put("keyword4", item("1-3 个工作日内到账"));
        data.put("remark",   item("感谢您对三水元的支持"));

        String url = publicBaseUrl + "/success/" + orderId;
        doSend(openid, templateId, url, data, WxMsgType.REFUND_SUCCESS, orderId);
    }

    private void doSend(String openid, String templateId, String url,
                        Map<String, Object> data, WxMsgType msgType, long orderId) {
        try {
            String token = tokenService.getToken();
            if (token.isBlank()) {
                log.warn("[wxmsg] access_token 为空，跳过推送 orderId={}", orderId);
                saveLog(WxMessageLog.failed(openid, msgType, orderId, templateId, "access_token empty"));
                return;
            }

            Map<String, Object> body = new HashMap<>();
            body.put("touser", openid);
            body.put("template_id", templateId);
            body.put("url", url);
            body.put("data", data);

            String bodyJson = objectMapper.writeValueAsString(body);
            String responseJson = restTemplate.postForObject(SEND_URL + token, bodyJson, String.class);

            var node = objectMapper.readTree(responseJson);
            int errCode = node.path("errcode").asInt(-1);
            if (errCode == 0) {
                String wxMsgId = node.path("msgid").asText("");
                saveLog(WxMessageLog.sent(openid, msgType, orderId, templateId, wxMsgId));
                log.info("[wxmsg] {} 推送成功 orderId={}", msgType, orderId);
            } else {
                String errmsg = node.path("errmsg").asText("");
                log.warn("[wxmsg] {} 推送失败 orderId={} errcode={} errmsg={}", msgType, orderId, errCode, errmsg);
                saveLog(WxMessageLog.failed(openid, msgType, orderId, templateId,
                        "errcode=" + errCode + " " + errmsg));
            }
        } catch (Exception e) {
            log.error("[wxmsg] {} 推送异常 orderId={}", msgType, orderId, e);
            saveLog(WxMessageLog.failed(openid, msgType, orderId, templateId, e.getMessage()));
        }
    }

    private void saveLog(WxMessageLog logEntry) {
        try {
            logRepository.save(logEntry);
        } catch (Exception e) {
            log.warn("[wxmsg] 写推送日志失败，忽略", e);
        }
    }

    private void saveSkipped(String openid, WxMsgType type, long orderId,
                              String templateId, WxMsgStatus reason) {
        try {
            if (!logRepository.existsByMsgTypeAndOrderIdAndStatus(type, orderId, reason)) {
                logRepository.save(WxMessageLog.skipped(openid, type, orderId, templateId, reason));
            }
        } catch (Exception e) {
            log.warn("[wxmsg] 写 SKIPPED 日志失败，忽略", e);
        }
    }

    private static Map<String, String> item(String value) {
        return Map.of("value", value);
    }

    private static String formatIso(String isoStr) {
        if (isoStr == null || isoStr.isBlank()) return "";
        try {
            return LocalDateTime.parse(isoStr.substring(0, 19)).format(DISPLAY_FMT);
        } catch (Exception e) {
            return isoStr;
        }
    }
}
