package com.sanshuiyuan.admin.infra.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * admin-service → ess-service 合同管理 BFF 客户端。
 * <p>
 * 封装 ess {@code /api/admin/contracts/*} 各端点，统一携带 {@code X-S2S-Token}（ess 的
 * {@code S2sTokenFilter} 校验）。管理员身份已由 admin-service {@code AdminJwtFilter} 校验。
 */
@Component
public class EssContractClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String s2sToken;

    public EssContractClient(RestTemplate restTemplate,
                             @Value("${ess-service.base-url:http://localhost:8085}") String baseUrl,
                             @Value("${ess-service.s2s-token:local-dev-static-token}") String s2sToken) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.s2sToken = s2sToken;
    }

    // ========== 列表 / 详情 / 检索 ==========

    public ResponseEntity<Map<String, Object>> list(MultiValueMap<String, String> query) {
        return getJson("/api/admin/contracts", query);
    }

    public ResponseEntity<Map<String, Object>> detail(Long id) {
        return getJson("/api/admin/contracts/" + id, null);
    }

    public ResponseEntity<Map<String, Object>> search(MultiValueMap<String, String> query) {
        return getJson("/api/admin/contracts/search", query);
    }

    public ResponseEntity<Map<String, Object>> audit(Long id, MultiValueMap<String, String> query) {
        return getJson("/api/admin/contracts/" + id + "/audit", query);
    }

    public ResponseEntity<Map<String, Object>> auditTrail(Long id, MultiValueMap<String, String> query) {
        return getJson("/api/admin/contracts/" + id + "/audit-trail", query);
    }

    // ========== 出证 ==========

    public ResponseEntity<Map<String, Object>> certificateInfo(Long contractId) {
        return getJson("/api/admin/contracts/certificate/" + contractId, null);
    }

    /** 出证 PDF 下载（透传二进制）。 */
    public ResponseEntity<byte[]> certificateDownload(Long contractId) {
        return proxyBytes("/api/admin/contracts/certificate/" + contractId + "/download");
    }

    /** 合同 PDF 代理预览（透传二进制，#38）。 */
    public ResponseEntity<byte[]> contractPdf(Long id) {
        return proxyBytes("/api/admin/contracts/" + id + "/pdf");
    }

    // ========== 主动查单（运维兜底） ==========

    public ResponseEntity<Map<String, Object>> reconcileSigning() {
        return postJson("/api/admin/contracts/reconcile-signing");
    }

    public ResponseEntity<Map<String, Object>> reconcileOne(Long id) {
        return postJson("/api/admin/contracts/" + id + "/reconcile-signing");
    }

    // ========== 失败重试（归档 / 出证） ==========

    public ResponseEntity<Map<String, Object>> retryArchive(Long id) {
        return postJson("/api/admin/contracts/" + id + "/retry-archive");
    }

    public ResponseEntity<Map<String, Object>> retryCertificate(Long id) {
        return postJson("/api/admin/contracts/" + id + "/retry-certificate");
    }

    // ========== 内部辅助 ==========

    /**
     * 二进制透传：把 ess 的非 2xx 状态（如合同 PDF 未归档 409、不存在 404）原样透传，
     * 而非被 RestTemplate 默认错误处理抛成 500（#44）。
     */
    private ResponseEntity<byte[]> proxyBytes(String path) {
        String url = build(path, null);
        try {
            return restTemplate.exchange(url, HttpMethod.GET, authEntity(), byte[].class);
        } catch (RestClientResponseException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .body(e.getResponseBodyAsByteArray());
        }
    }

    private ResponseEntity<Map<String, Object>> getJson(String path, MultiValueMap<String, String> query) {
        return restTemplate.exchange(build(path, query), HttpMethod.GET, authEntity(), MAP_TYPE);
    }

    private ResponseEntity<Map<String, Object>> postJson(String path) {
        return restTemplate.exchange(build(path, null), HttpMethod.POST, authEntity(), MAP_TYPE);
    }

    private String build(String path, MultiValueMap<String, String> query) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl).path(path);
        if (query != null && !query.isEmpty()) {
            builder.queryParams(query);
        }
        return builder.toUriString();
    }

    private HttpEntity<Void> authEntity() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-S2S-Token", s2sToken);
        return new HttpEntity<>(headers);
    }
}
