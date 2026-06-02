package com.sanshuiyuan.logistics.application;

import com.sanshuiyuan.logistics.domain.LogisticsOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * INSTALLED 事件发布器：事务提交后，通过 service-to-service token 调用
 * matching-service POST /internal/matching/fulfill，完成跨服务联动。
 */
@Component
public class InstalledEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(InstalledEventPublisher.class);

    private final RestTemplate restTemplate;
    private final String matchingInternalUrl;
    private final String s2sToken;

    public InstalledEventPublisher(
            @Value("${matching.internal-url:http://localhost:8086}") String matchingInternalUrl,
            @Value("${s2s.token:dev-s2s-shared-token}") String s2sToken) {
        this.restTemplate = new RestTemplate();
        this.matchingInternalUrl = matchingInternalUrl;
        this.s2sToken = s2sToken;
    }

    /**
     * 事务提交后异步通知 matching-service。失败由 Spring Retry 自动重试（指数退避 5 次），
     * 最终失败仅记录日志（不阻塞主事务——主事务已提交），由运维通过告警表补偿。
     */
    @Retryable(maxAttempts = 5, backoff = @Backoff(
            delay = 2000, multiplier = 2.0, maxDelay = 30000))
    public void publishAfterCommit(LogisticsOrder order) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + s2sToken);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("request_id", order.getRequestId());
            body.put("device_asset_id", order.getDeviceAssetId());
            body.put("logistics_order_id", order.getId());

            HttpEntity<Map<String, Object>> request =
                    new HttpEntity<>(body, headers);

            String url = matchingInternalUrl + "/internal/matching/fulfill";
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("INSTALLED 跨服务通知失败: HTTP {} body={}",
                        response.getStatusCode(), response.getBody());
                throw new RuntimeException("matching fulfill 返回非 2xx: " + response.getStatusCode());
            }

            log.info("INSTALLED 跨服务通知成功: order={} request={} device={}",
                    order.getId(), order.getRequestId(), order.getDeviceAssetId());
        } catch (RestClientException e) {
            log.error("INSTALLED 跨服务调用异常: order={} msg={}", order.getId(), e.getMessage());
            throw new RuntimeException("matching fulfill 网络异常", e);
        }
    }
}
