package com.sanshuiyuan.ess.controller;

import com.sanshuiyuan.ess.config.SecurityConfig;
import com.sanshuiyuan.ess.domain.Contract;
import com.sanshuiyuan.ess.domain.ContractAccessLog;
import com.sanshuiyuan.ess.infra.repository.ContractAccessLogRepository;
import com.sanshuiyuan.ess.infra.repository.ContractRepository;
import com.sanshuiyuan.ess.infra.repository.ContractSnBindingRepository;
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
 * T20.9-T20.11: 管理后台合同管理控制器测试。
 */
@WebMvcTest(ContractAdminController.class)
@Import(SecurityConfig.class)
@ExtendWith(MockitoExtension.class)
class ContractAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ContractQueryService queryService;

    @MockBean
    private ContractAccessLogService accessLogService;

    @MockBean
    private ContractArchiveService archiveService;

    @MockBean
    private ContractRepository contractRepository;

    @MockBean
    private ContractAccessLogRepository accessLogRepository;

    @MockBean
    private ContractSnBindingRepository snBindingRepository;

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
}
