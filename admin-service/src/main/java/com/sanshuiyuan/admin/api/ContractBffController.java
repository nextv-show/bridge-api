package com.sanshuiyuan.admin.api;

import com.sanshuiyuan.admin.infra.client.EssContractClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理后台合同 BFF：镜像 ess-service {@code /api/admin/contracts/*}，委托 {@link EssContractClient}。
 * <p>
 * 鉴权由 admin-service 的 {@code AdminJwtFilter}（anyRequest authenticated）自动生效；
 * 下游 S2S token 由 client 注入。本控制器仅做参数透传与响应转发，不含业务逻辑。
 */
@RestController
@RequestMapping("/admin/contracts")
public class ContractBffController {

    private final EssContractClient essContractClient;

    public ContractBffController(EssContractClient essContractClient) {
        this.essContractClient = essContractClient;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(@RequestParam MultiValueMap<String, String> params) {
        return essContractClient.list(params);
    }

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(@RequestParam MultiValueMap<String, String> params) {
        return essContractClient.search(params);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable Long id) {
        return essContractClient.detail(id);
    }

    @GetMapping("/{id}/audit")
    public ResponseEntity<Map<String, Object>> audit(@PathVariable Long id,
                                                     @RequestParam MultiValueMap<String, String> params) {
        return essContractClient.audit(id, stripPathVars(params));
    }

    @GetMapping("/{id}/audit-trail")
    public ResponseEntity<Map<String, Object>> auditTrail(@PathVariable Long id,
                                                          @RequestParam MultiValueMap<String, String> params) {
        return essContractClient.auditTrail(id, stripPathVars(params));
    }

    @GetMapping("/certificate/{contractId}")
    public ResponseEntity<Map<String, Object>> certificateInfo(@PathVariable Long contractId) {
        return essContractClient.certificateInfo(contractId);
    }

    @GetMapping("/certificate/{contractId}/download")
    public ResponseEntity<byte[]> certificateDownload(@PathVariable Long contractId) {
        ResponseEntity<byte[]> resp = essContractClient.certificateDownload(contractId);
        MediaType contentType = resp.getHeaders().getContentType();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(contentType != null ? contentType : MediaType.APPLICATION_PDF);
        String disposition = resp.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        if (disposition != null) {
            headers.set(HttpHeaders.CONTENT_DISPOSITION, disposition);
        }
        return new ResponseEntity<>(resp.getBody(), headers, resp.getStatusCode());
    }

    @PostMapping("/reconcile-signing")
    public ResponseEntity<Map<String, Object>> reconcileSigning() {
        return essContractClient.reconcileSigning();
    }

    @PostMapping("/{id}/reconcile-signing")
    public ResponseEntity<Map<String, Object>> reconcileOne(@PathVariable Long id) {
        return essContractClient.reconcileOne(id);
    }

    @PostMapping("/{id}/retry-archive")
    public ResponseEntity<Map<String, Object>> retryArchive(@PathVariable Long id) {
        return essContractClient.retryArchive(id);
    }

    @PostMapping("/{id}/retry-certificate")
    public ResponseEntity<Map<String, Object>> retryCertificate(@PathVariable Long id) {
        return essContractClient.retryCertificate(id);
    }

    /** @RequestParam MultiValueMap 不含 path 变量，这里仅作防御性拷贝以免透传意外键。 */
    private MultiValueMap<String, String> stripPathVars(MultiValueMap<String, String> params) {
        MultiValueMap<String, String> copy = new LinkedMultiValueMap<>();
        if (params != null) {
            copy.putAll(params);
        }
        return copy;
    }
}
