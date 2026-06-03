package com.sanshuiyuan.ess.controller;

import com.sanshuiyuan.ess.config.SecurityConfig;
import com.sanshuiyuan.ess.domain.Contract;
import com.sanshuiyuan.ess.domain.ContractAccessLog;
import com.sanshuiyuan.ess.domain.ContractAuditTrail;
import com.sanshuiyuan.ess.infra.repository.ContractAccessLogRepository;
import com.sanshuiyuan.ess.infra.repository.ContractRepository;
import com.sanshuiyuan.ess.infra.repository.ContractSnBindingRepository;
import com.sanshuiyuan.ess.service.AuditTrailService;
import com.sanshuiyuan.ess.service.CertificateService;
import com.sanshuiyuan.ess.service.ContractAccessLogService;
import com.sanshuiyuan.ess.service.ContractArchiveService;
import com.sanshuiyuan.ess.service.ContractQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * T20.9-T20.11, T22.8-T22.11: 管理后台合同管理控制器测试。
 */
@WebMvcTest(ContractAdminController.class)
@Import(SecurityConfig.class)
@ExtendWith(MockitoExtension.class)
class ContractAdminControllerTest {

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.web.context.WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    /**
     * /api/admin/* 现由 S2sTokenFilter 保护：默认给每个请求带上正确的 X-S2S-Token
     * （application.yml 默认 local-dev-static-token），让既有业务断言继续聚焦在 controller 行为。
     * S2S 鉴权拒绝路径（缺/错 token → 401）在 S2sTokenFilterTest 中独立断言。
     */
    @org.junit.jupiter.api.BeforeEach
    void setUpMockMvc() {
        this.mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .defaultRequest(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/")
                        .header("X-S2S-Token", "local-dev-static-token"))
                .build();
    }

    @MockBean
    private ContractQueryService queryService;

    @MockBean
    private ContractAccessLogService accessLogService;

    @MockBean
    private ContractArchiveService archiveService;

    @MockBean
    private CertificateService certificateService;

    @MockBean
    private AuditTrailService auditTrailService;

    @MockBean
    private ContractRepository contractRepository;

    @MockBean
    private ContractAccessLogRepository accessLogRepository;

    @MockBean
    private ContractSnBindingRepository snBindingRepository;

    @MockBean
    private com.sanshuiyuan.ess.service.ReconcileSigningContractsJob reconcileJob;

    @MockBean
    private com.sanshuiyuan.ess.service.ContractCompletionBridge completionBridge;

    @MockBean
    private com.sanshuiyuan.ess.service.EssContractService essContractService;

    // ========== T20.9: GET /api/admin/contracts ==========

    @Test
    void listContracts_shouldReturnPagedResults() throws Exception {
        Contract contract = Contract.createDraft("CT-20260527-ADM01", 1L, 100L, "ORD-ADM", "SN-ADM");
        contract.markGenerated("{}", "{}");
        contract.startSigning("flow-adm");
        contract.completeSigning("https://pdf.example.com/adm.pdf", "hash-adm");
        contract.archive();

        Page<Contract> page = new PageImpl<>(List.of(contract), PageRequest.of(0, 20), 1);
        when(queryService.searchContracts(isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), any())).thenReturn(page);

        mockMvc.perform(get("/api/admin/contracts")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].contractNo").value("CT-20260527-ADM01"));
    }

    @Test
    void listContracts_withFilters_shouldReturnFiltered() throws Exception {
        Contract contract = Contract.createDraft("CT-FILTER-001", 1L, 200L, "ORD-FILTER", "SN-FILTER");
        contract.markGenerated("{}", "{}");

        Page<Contract> page = new PageImpl<>(List.of(contract), PageRequest.of(0, 20), 1);
        when(queryService.searchContracts(eq("ORD-FILTER"), isNull(), eq(200L), isNull(), isNull(),
                isNull(), isNull(), any())).thenReturn(page);

        mockMvc.perform(get("/api/admin/contracts")
                        .param("orderId", "ORD-FILTER")
                        .param("userId", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.total").value(1));
    }

    // ========== T20.10: GET /api/admin/contracts/{id} ==========

    @Test
    void getContractDetail_shouldReturnFullAuditInfo() throws Exception {
        Contract contract = Contract.createDraft("CT-DETAIL-001", 1L, 300L, "ORD-DETAIL", "SN-DETAIL");
        contract.markGenerated("{}", "{}");
        contract.startSigning("flow-detail");
        contract.completeSigning("https://pdf.example.com/detail.pdf", "hash-detail-abc123");
        contract.updateArchiveUrls(
                "https://cos.ap-guangzhou.myqcloud.com/sanshuiyuan-contracts/contracts/CT-DETAIL-001.pdf",
                "https://oss.sanshuiyuan.com/contracts/CT-DETAIL-001.pdf",
                "hash-detail-abc123");
        contract.archive();

        when(queryService.getContractDetail(5L)).thenReturn(contract);
        when(accessLogService.countByContractIdAndAccessType(5L, ContractAccessLog.AccessType.VIEW))
                .thenReturn(3L);
        when(accessLogService.countByContractIdAndAccessType(5L, ContractAccessLog.AccessType.DOWNLOAD))
                .thenReturn(1L);

        mockMvc.perform(get("/api/admin/contracts/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.contractNo").value("CT-DETAIL-001"))
                .andExpect(jsonPath("$.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.pdfHash").value("hash-detail-abc123"))
                .andExpect(jsonPath("$.tencentCloudUrl").exists())
                .andExpect(jsonPath("$.ossUrl").exists())
                .andExpect(jsonPath("$.viewAccessCount").value(3))
                .andExpect(jsonPath("$.downloadAccessCount").value(1));
    }

    @Test
    void getContractDetail_notFound_shouldFail() throws Exception {
        when(queryService.getContractDetail(9999L))
                .thenThrow(new IllegalArgumentException("合同不存在: id=9999"));

        mockMvc.perform(get("/api/admin/contracts/9999"))
                .andExpect(status().is5xxServerError());
    }

    // ========== T20.11: GET /api/admin/contracts/{id}/audit ==========

    @Test
    void getContractAuditLog_shouldReturnAccessLogs() throws Exception {
        Contract contract = Contract.createDraft("CT-AUDIT-001", 1L, 400L, "ORD-AUDIT", "SN-AUDIT");
        when(queryService.getContractDetail(8L)).thenReturn(contract);

        ContractAccessLog log = ContractAccessLog.create(8L, 400L,
                ContractAccessLog.AccessType.VIEW, ContractAccessLog.AccessSource.H5,
                "192.168.1.1", "Mozilla/5.0");

        Page<ContractAccessLog> logPage = new PageImpl<>(List.of(log), PageRequest.of(0, 20), 1);
        when(accessLogService.getAccessLogs(eq(8L), any())).thenReturn(logPage);

        mockMvc.perform(get("/api/admin/contracts/8/audit")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.contractId").value(8))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].accessType").value("VIEW"))
                .andExpect(jsonPath("$.items[0].ipAddress").value("192.168.1.1"));
    }

    @Test
    void getContractAuditLog_emptyLogs_shouldReturnEmptyList() throws Exception {
        Contract contract = Contract.createDraft("CT-AUDIT-002", 1L, 500L, "ORD-AUDIT2", "SN-AUDIT2");
        when(queryService.getContractDetail(9L)).thenReturn(contract);

        Page<ContractAccessLog> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0);
        when(accessLogService.getAccessLogs(eq(9L), any())).thenReturn(emptyPage);

        mockMvc.perform(get("/api/admin/contracts/9/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    // ========== T22.8: GET /api/admin/contracts/certificate/{contractId} ==========

    @Test
    void getCertificateInfo_shouldReturnCertStatus() throws Exception {
        Contract contract = Contract.createDraft("CT-CERT-001", 1L, 600L, "ORD-CERT", "SN-CERT");
        contract.markGenerated("{}", "{}");
        contract.startSigning("flow-cert");
        contract.completeSigning("https://pdf.example.com/cert.pdf", "hash-cert");
        contract.archive();
        contract.completeCertificate("CERT-20260527-001", "https://cert.example.com/cert.pdf");

        when(queryService.getContractDetail(10L)).thenReturn(contract);
        when(certificateService.queryCertificateStatus(10L))
                .thenReturn(new CertificateService.CertificateResult(
                        true, 10L, "CT-CERT-001", "CERT-20260527-001",
                        "https://cert.example.com/cert.pdf", "CERTIFIED",
                        LocalDateTime.of(2026, 5, 27, 12, 0, 0).toString()));

        mockMvc.perform(get("/api/admin/contracts/certificate/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.contractId").value(10))
                .andExpect(jsonPath("$.certificateStatus").value("CERTIFIED"))
                .andExpect(jsonPath("$.certificateNo").value("CERT-20260527-001"))
                .andExpect(jsonPath("$.certificatePdfUrl").value("https://cert.example.com/cert.pdf"));
    }

    @Test
    void getCertificateInfo_notApplied_shouldReturnNotApplied() throws Exception {
        Contract contract = Contract.createDraft("CT-NOCERT-001", 1L, 700L, "ORD-NO", "SN-NO");
        contract.markGenerated("{}", "{}");
        when(queryService.getContractDetail(11L)).thenReturn(contract);
        when(certificateService.queryCertificateStatus(11L))
                .thenReturn(CertificateService.CertificateResult.notApplied(11L, "CT-NOCERT-001"));

        mockMvc.perform(get("/api/admin/contracts/certificate/11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.certificateStatus").value("NOT_APPLIED"))
                .andExpect(jsonPath("$.certificateNo").value(""));
    }

    // ========== T22.9: GET /api/admin/contracts/certificate/{contractId}/download ==========

    @Test
    void downloadCertificate_shouldReturnDownloadUrl() throws Exception {
        when(certificateService.queryCertificateStatus(20L))
                .thenReturn(new CertificateService.CertificateResult(
                        true, 20L, "CT-DOWN-001", "CERT-DOWN-001",
                        "https://cert.example.com/download.pdf", "CERTIFIED", null));

        mockMvc.perform(get("/api/admin/contracts/certificate/20/download"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.certificateNo").value("CERT-DOWN-001"))
                .andExpect(jsonPath("$.downloadUrl").value("https://cert.example.com/download.pdf"));
    }

    @Test
    void downloadCertificate_notCertified_shouldReturnError() throws Exception {
        when(certificateService.queryCertificateStatus(21L))
                .thenReturn(CertificateService.CertificateResult.notApplied(21L, "CT-NOPE"));

        mockMvc.perform(get("/api/admin/contracts/certificate/21/download"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.message").value("合同尚未完成出证或出证 PDF 不可用"));
    }

    // ========== T22.10: GET /api/admin/contracts/search ==========

    @Test
    void searchContracts_shouldReturnEnhancedResults() throws Exception {
        Contract contract = Contract.createDraft("CT-SEARCH-001", 1L, 800L, "ORD-SEARCH", "SN-SEARCH");
        contract.markGenerated("{}", "{}");
        contract.completeCertificate("CERT-SEARCH-001", "https://cert.example.com/s.pdf");

        Page<Contract> page = new PageImpl<>(List.of(contract), PageRequest.of(0, 20), 1);
        when(queryService.searchContracts(isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), any())).thenReturn(page);

        mockMvc.perform(get("/api/admin/contracts/search")
                        .param("certificateStatus", "CERTIFIED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].certificateStatus").value("CERTIFIED"))
                .andExpect(jsonPath("$.items[0].certificateNo").value("CERT-SEARCH-001"));
    }

    @Test
    void searchContracts_withNoCertificateFilter_shouldReturnAll() throws Exception {
        Contract contract = Contract.createDraft("CT-SEARCH-002", 1L, 900L, "ORD-ALL", "SN-ALL");
        contract.markGenerated("{}", "{}");

        Page<Contract> page = new PageImpl<>(List.of(contract), PageRequest.of(0, 20), 1);
        when(queryService.searchContracts(isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), any())).thenReturn(page);

        mockMvc.perform(get("/api/admin/contracts/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.total").value(1));
    }

    // ========== T22.11: GET /api/admin/contracts/{id}/audit-trail ==========

    @Test
    void getAuditTrail_shouldReturnTrailEvents() throws Exception {
        Contract contract = Contract.createDraft("CT-TRAIL-001", 1L, 1000L, "ORD-TRAIL", "SN-TRAIL");
        when(queryService.getContractDetail(30L)).thenReturn(contract);

        ContractAuditTrail trail = ContractAuditTrail.create(30L,
                ContractAuditTrail.Action.CREATE, "1000",
                ContractAuditTrail.ActorType.USER,
                "{\"orderId\":\"ORD-TRAIL\"}", "10.0.0.1");

        Page<ContractAuditTrail> trailPage = new PageImpl<>(List.of(trail), PageRequest.of(0, 20), 1);
        when(auditTrailService.getAuditTrail(eq(30L), any())).thenReturn(trailPage);

        mockMvc.perform(get("/api/admin/contracts/30/audit-trail")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.contractId").value(30))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].action").value("CREATE"))
                .andExpect(jsonPath("$.items[0].actorType").value("USER"))
                .andExpect(jsonPath("$.items[0].ipAddress").value("10.0.0.1"));
    }

    @Test
    void getAuditTrail_empty_shouldReturnEmptyList() throws Exception {
        Contract contract = Contract.createDraft("CT-TRAIL-002", 1L, 1100L, "ORD-EMPTY", "SN-EMPTY");
        when(queryService.getContractDetail(31L)).thenReturn(contract);

        Page<ContractAuditTrail> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0);
        when(auditTrailService.getAuditTrail(eq(31L), any())).thenReturn(emptyPage);

        mockMvc.perform(get("/api/admin/contracts/31/audit-trail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void getAuditTrail_contractNotFound_shouldFail() throws Exception {
        when(queryService.getContractDetail(9999L))
                .thenThrow(new IllegalArgumentException("合同不存在: id=9999"));

        mockMvc.perform(get("/api/admin/contracts/9999/audit-trail"))
                .andExpect(status().is5xxServerError());
    }
}
