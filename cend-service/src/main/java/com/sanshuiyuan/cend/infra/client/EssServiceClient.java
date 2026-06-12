package com.sanshuiyuan.cend.infra.client;

import com.sanshuiyuan.cend.common.BizException;
import com.sanshuiyuan.cend.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 调用 ess-service 的 /api/c/contracts/* 合同接口（小程序认购签约编排用）。
 * 转发用户的 H5 会话 token（与 H5 网页端调 ess 一致），不引入新的 S2S 凭证。
 * ess 接口返回原始 {@code {code, ...}}（非 ApiResponse 包裹），以 Map 接收后读字段。
 */
@Component
public class EssServiceClient {

    private static final Logger log = LoggerFactory.getLogger(EssServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public EssServiceClient(RestTemplate restTemplate,
                            @Value("${ess-service.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public record GenerateResult(Long contractId, String contractNo, String status) {}

    /** 生成合同 POST /api/c/contracts/generate。 */
    public GenerateResult generate(String bearer, Long userId, String deviceModel, String devicePrice,
                                   String userName, String idCardNo, String phone) {
        Map<String, Object> body = new LinkedHashMap<>();
        // userId 仅作参考：ess 以会话 openid 为准解析。为 null 时省略，勿发字符串 "null"。
        if (userId != null) body.put("userId", String.valueOf(userId));
        body.put("deviceModel", deviceModel);
        body.put("devicePrice", devicePrice);
        body.put("userName", userName);
        body.put("idCardNo", idCardNo);
        body.put("phone", phone);
        Map<String, Object> resp = post(bearer, "/api/c/contracts/generate", body, "生成合同");
        Long contractId = asLong(resp.get("contractId"));
        if (contractId == null) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, str(resp.get("message"), "生成合同失败"));
        }
        return new GenerateResult(contractId, str(resp.get("contractNo"), null), str(resp.get("status"), null));
    }

    /** 发起签署 POST /api/c/contracts/{id}/initiate-signing（clientType=MINI）。 */
    public String initiateSigning(String bearer, Long contractId, Long userId,
                                  String phone, String realName, String realIdCard) {
        Map<String, Object> body = new LinkedHashMap<>();
        // userId 仅作参考：ess 以会话 openid 为准解析。为 null 时省略，勿发字符串 "null"。
        if (userId != null) body.put("userId", String.valueOf(userId));
        body.put("clientType", "MINI");
        // notify=true：小程序认购走「短信短链」签署——ess 侧给签署方 NotifyType=SMS 并 StartFlow，
        // 由腾讯电子签下发带签署短链的短信；用户在浏览器签完，本端轮询 sign-status 进入支付。不再跳转电子签小程序。
        body.put("notify", "true");
        if (phone != null) body.put("phone", phone);
        if (realName != null) body.put("realName", realName);
        if (realIdCard != null) body.put("realIdCard", realIdCard);
        Map<String, Object> resp = post(bearer, "/api/c/contracts/" + contractId + "/initiate-signing", body, "发起签署");
        return str(resp.get("status"), null);
    }

    /** 查询合同状态 GET /api/c/contracts/{id}/status。 */
    public String status(String bearer, Long contractId) {
        Map<String, Object> resp = get(bearer, baseUrl + "/api/c/contracts/" + contractId + "/status", "查询合同状态");
        return str(resp.get("status"), null);
    }

    // ───────────── helpers ─────────────

    private Map<String, Object> post(String bearer, String path, Map<String, Object> body, String op) {
        HttpHeaders headers = baseHeaders(bearer);
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.postForObject(
                    baseUrl + path, new HttpEntity<>(body, headers), Map.class);
            return resp == null ? Map.of() : resp;
        } catch (Exception e) {
            log.error("{} 调 ess 失败 path={}: {}", op, path, e.getMessage());
            throw new BizException(ErrorCode.INTERNAL_ERROR, op + "失败，请重试");
        }
    }

    private Map<String, Object> get(String bearer, String url, String op) {
        try {
            @SuppressWarnings("unchecked")
            var entity = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET,
                    new HttpEntity<>(baseHeaders(bearer)), Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = entity.getBody();
            return resp == null ? Map.of() : resp;
        } catch (Exception e) {
            log.error("{} 调 ess 失败 url={}: {}", op, url, e.getMessage());
            throw new BizException(ErrorCode.INTERNAL_ERROR, op + "失败，请重试");
        }
    }

    private static HttpHeaders baseHeaders(String bearer) {
        HttpHeaders headers = new HttpHeaders();
        if (bearer != null && !bearer.isBlank()) {
            headers.set("Authorization", bearer.startsWith("Bearer ") ? bearer : "Bearer " + bearer);
        }
        headers.set("X-Client-Type", "MINI");
        return headers;
    }

    private static Long asLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try {
            return Long.valueOf(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String str(Object o, String def) {
        return o == null ? def : o.toString();
    }
}
