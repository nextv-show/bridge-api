package com.sanshuiyuan.matching.request.application;

import com.sanshuiyuan.matching.request.infra.DeviceAssetGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 110 SN 回写真实实现：设备履约/激活推进成功后 S2S 调用 cend-service
 * POST /internal/orders/bind-sn，把真实 SN 回写到 h5_orders.sn。
 *
 * <p>激活条件 {@code matching.notify.cend-base-url} 已配置；未配置时由
 * {@link NoOpOrderSnBindbackNotifier} 日志降级（两 bean 以互补 {@code @ConditionalOnProperty} 互斥）。
 * 即便 cend-base-url 为空字符串（误活），{@link #notifyBindSn} 仍做空值兜底跳过；
 * 全程 try-catch，绝不抛出，不阻塞履约/激活主流程。
 */
@Component
@ConditionalOnProperty(name = "matching.notify.cend-base-url")
public class CendOrderSnBindbackNotifier implements OrderSnBindbackNotifier {

    private static final Logger log = LoggerFactory.getLogger(CendOrderSnBindbackNotifier.class);

    private final DeviceAssetGateway deviceAssetGateway;
    private final String cendBaseUrl;
    private final String s2sToken;
    // 非 final 包级可见：单测在同包内替换为 Mockito mock（与 WxMsgClaimConfirmNotifier 同样设计）。
    RestTemplate restTemplate = new RestTemplate();

    public CendOrderSnBindbackNotifier(DeviceAssetGateway deviceAssetGateway,
                                       @Value("${matching.notify.cend-base-url:}") String cendBaseUrl,
                                       @Value("${s2s.token:dev-s2s-shared-token}") String s2sToken) {
        this.deviceAssetGateway = deviceAssetGateway;
        this.cendBaseUrl = cendBaseUrl;
        this.s2sToken = s2sToken;
    }

    @Override
    public void notifyBindSn(long deviceAssetId, String sn) {
        if (cendBaseUrl == null || cendBaseUrl.isBlank()) {
            log.debug("SN 回写 device_asset_id={} 跳过：cend-base-url 未配置", deviceAssetId);
            return;
        }

        // sn 入参为空时按 device_asset_id 反查 device_assets.sn
        String realSn = (sn == null || sn.isBlank())
                ? deviceAssetGateway.findSnById(deviceAssetId)
                : sn;
        if (realSn == null || realSn.isBlank() || realSn.startsWith("SN-PENDING")) {
            log.debug("SN 回写 device_asset_id={} 跳过：无真实 sn（{}）", deviceAssetId, realSn);
            return;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + s2sToken);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("device_asset_id", deviceAssetId);
            payload.put("sn", realSn);

            String url = cendBaseUrl + "/internal/orders/bind-sn";
            ResponseEntity<String> resp = restTemplate.postForEntity(
                    url, new HttpEntity<>(payload, headers), String.class);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                log.warn("SN 回写 device_asset_id={} 返回非 2xx：{} body={}",
                        deviceAssetId, resp.getStatusCode(), resp.getBody());
            } else {
                log.info("SN 回写 device_asset_id={} sn={} 已下发 cend", deviceAssetId, realSn);
            }
        } catch (Exception e) {
            // 仅告警，绝不抛出：SN 回写是 best-effort 兜底，失败不得中断履约/激活。
            log.warn("SN 回写 device_asset_id={} 异常：{}", deviceAssetId, e.getMessage());
        }
    }
}
