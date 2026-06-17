package com.sanshuiyuan.matching.request.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * P1-2 接单确认提醒真实实现（008 通道）：反查 owner openid 后 S2S 调用 cend-service
 * POST /internal/wxmsg/claim-reminder，复用 cend 的微信模板消息基础设施推送公众号模板消息。
 *
 * <p>激活条件 {@code matching.notify.cend-base-url} 已配置；未配置时由
 * {@link NoOpClaimConfirmNotifier} 日志降级（两 bean 以互补 {@code @ConditionalOnProperty} 互斥）。
 * 即便 cend-base-url 为空字符串（误活），{@link #remind} 仍做空值兜底跳过，绝不阻塞 SLA 任务。
 */
@Component
@ConditionalOnProperty(name = "matching.notify.cend-base-url")
public class WxMsgClaimConfirmNotifier implements ClaimConfirmNotifier {

    private static final Logger log = LoggerFactory.getLogger(WxMsgClaimConfirmNotifier.class);

    private final JdbcTemplate jdbcTemplate;
    private final String cendBaseUrl;
    private final String s2sToken;
    // 非 final 包级可见：单测在同包内替换为 Mockito mock（与 InstalledEventPublisher 同样 new 不可注入，故此设计）。
    RestTemplate restTemplate = new RestTemplate();

    public WxMsgClaimConfirmNotifier(JdbcTemplate jdbcTemplate,
                                     @Value("${matching.notify.cend-base-url:}") String cendBaseUrl,
                                     @Value("${s2s.token:dev-s2s-shared-token}") String s2sToken) {
        this.jdbcTemplate = jdbcTemplate;
        this.cendBaseUrl = cendBaseUrl;
        this.s2sToken = s2sToken;
    }

    @Override
    public void remind(long requestId, Long ownerUserId, Stage stage) {
        if (cendBaseUrl == null || cendBaseUrl.isBlank()) {
            log.debug("claim 确认提醒[{}] request_id={} 跳过：cend-base-url 未配置", stage, requestId);
            return;
        }
        if (ownerUserId == null) {
            log.info("claim 确认提醒[{}] request_id={} 跳过：owner 为空", stage, requestId);
            return;
        }

        String openid = findOpenid(ownerUserId);
        if (openid == null || openid.isBlank()) {
            log.info("claim 确认提醒[{}] request_id={} owner={} 跳过：openid 为空", stage, requestId, ownerUserId);
            return;
        }

        String stageLabel;
        String deadlineDisplay;
        if (stage == Stage.FINAL) {
            stageLabel = "最后提醒（即将自动释放）";
            deadlineDisplay = "请立即确认";
        } else {
            stageLabel = "温馨提醒（12小时）";
            deadlineDisplay = "请在 12 小时内确认";
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + s2sToken);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("openid", openid);
            payload.put("request_id", requestId);
            payload.put("stage", stage.name());
            payload.put("stage_label", stageLabel);
            payload.put("deadline_display", deadlineDisplay);

            String url = cendBaseUrl + "/internal/wxmsg/claim-reminder";
            ResponseEntity<String> resp = restTemplate.postForEntity(
                    url, new HttpEntity<>(payload, headers), String.class);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                log.warn("claim 确认提醒[{}] request_id={} 推送返回非 2xx：{} body={}",
                        stage, requestId, resp.getStatusCode(), resp.getBody());
            } else {
                log.info("claim 确认提醒[{}] request_id={} owner={} 已下发 cend 推送", stage, requestId, ownerUserId);
            }
        } catch (Exception e) {
            // 仅告警，绝不抛出：SLA 扫描任务批量处理多个需求，单个推送失败不得中断后续释放/提醒。
            log.warn("claim 确认提醒[{}] request_id={} 推送异常：{}", stage, requestId, e.getMessage());
        }
    }

    private String findOpenid(long userId) {
        List<String> rows = jdbcTemplate.queryForList(
                "SELECT openid FROM users WHERE id = ?", String.class, userId);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
