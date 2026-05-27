package com.sanshuiyuan.ess.controller;

import com.sanshuiyuan.ess.config.SecurityConfig;
import com.sanshuiyuan.ess.domain.Contract;
import com.sanshuiyuan.ess.domain.ContractAccessLog;
import com.sanshuiyuan.ess.domain.ContractSnBinding;
import com.sanshuiyuan.ess.infra.repository.ContractAccessLogRepository;
import com.sanshuiyuan.ess.infra.repository.ContractSnBindingRepository;
import com.sanshuiyuan.ess.service.AuditTrailService;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * T20.6-T20.8: H5 合同查看/下载/SN查询控制器测试。
 */
@WebMvcTest(ContractViewController.class)
@Import(SecurityConfig.class)
@ExtendWith(MockitoExtension.class)
class ContractViewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ContractArchiveService archiveService;

    @MockBean
    private ContractQueryService queryService;

    @MockBean
    private ContractAccessLogService accessLogService;

    @MockBean
    private ContractSnBindingRepository snBindingRepository;

    @MockBean
    private ContractAccessLogRepository accessLogRepository;

    @MockBean
    private AuditTrailService auditTrailService;

    // ========== T20.6: GET /api/h5/contracts/{id}/view ==========

    @Test
    void viewContract_shouldReturnViewUrl() throws Exception {
        Contract contract = Contract.createDraft("CT-20260527-V001", 1L, 100L, "ORD-001", "SN-001");
        contract.markGenerated("{}", "{}");
        contract.startSigning("flow-001");
        contract.completeSigning("https://pdf.example.com/c.pdf", "hash123");
        contract.archive();

        when(archiveService.getViewUrl(1L)).thenReturn("https://oss.sanshuiyuan.com/contracts/CT-20260527-V001.pdf?sign=stub");
        when(queryService.getContractDetail(1L)).thenReturn(contract);
        when(accessLogService.logAccess(eq(1L), isNull(), any(), any(), any(), any()))
                .thenReturn(ContractAccessLog.create(1L, null,
                        ContractAccessLog.AccessType.VIEW, ContractAccessLog.AccessSource.H5, "127.0.0.1", "test"));

        mockMvc.perform(get("/api/h5/contracts/1/view"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.contractId").value(1))
                .andExpect(jsonPath("$.contractNo").value("CT-20260527-V001"))
                .andExpect(jsonPath("$.viewUrl").exists());
    }

    @Test
    void viewContract_notArchived_shouldFail() throws Exception {
        when(archiveService.getViewUrl(999L))
                .thenThrow(new IllegalStateException("合同尚未归档，无法查看"));

        mockMvc.perform(get("/api/h5/contracts/999/view"))
                .andExpect(status().is5xxServerError());
    }

    // ========== T20.7: GET /api/h5/contracts/{id}/download ==========

    @Test
    void downloadContract_shouldReturnDownloadUrl() throws Exception {
        Contract contract = Contract.createDraft("CT-20260527-D001", 1L, 100L, "ORD-002", "SN-002");
        contract.markGenerated("{}", "{}");
        contract.startSigning("flow-002");
        contract.completeSigning("https://pdf.example.com/d.pdf", "hash456");
        contract.archive();

        when(archiveService.getDownloadUrl(2L)).thenReturn("https://oss.sanshuiyuan.com/contracts/CT-20260527-D001.pdf?sign=stub&expire=300");
        when(queryService.getContractDetail(2L)).thenReturn(contract);
        when(accessLogService.logAccess(eq(2L), isNull(), any(), any(), any(), any()))
                .thenReturn(ContractAccessLog.create(2L, null,
                        ContractAccessLog.AccessType.DOWNLOAD, ContractAccessLog.AccessSource.H5, "127.0.0.1", "test"));

        mockMvc.perform(get("/api/h5/contracts/2/download"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.downloadUrl").exists())
                .andExpect(jsonPath("$.pdfHash").value("hash456"));
    }

    // ========== T20.8: GET /api/h5/devices/{sn}/contract ==========

    @Test
    void getDeviceContract_shouldReturnContract() throws Exception {
        Contract contract = Contract.createDraft("CT-20260527-SN001", 1L, 100L, "ORD-SN", "SN-TEST-001");
        contract.markGenerated("{}", "{}");
        contract.startSigning("flow-sn");
        contract.completeSigning("https://pdf.example.com/sn.pdf", "hash-sn");
        contract.archive();

        // Use reflection to set the ID since JPA normally generates it
        var idField = Contract.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(contract, 10L);

        var binding = ContractSnBinding.preAllocate(10L, "SN-TEST-001");

        when(snBindingRepository.findByDeviceSn("SN-TEST-001"))
                .thenReturn(Collections.singletonList(binding));
        when(queryService.getContractDetail(10L)).thenReturn(contract);
        when(archiveService.getViewUrl(10L)).thenReturn("https://oss.example.com/view");

        mockMvc.perform(get("/api/h5/devices/SN-TEST-001/contract"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.contractId").value(10))
                .andExpect(jsonPath("$.deviceSn").value("SN-TEST-001"));
    }

    @Test
    void getDeviceContract_notFound_shouldReturnMessage() throws Exception {
        when(snBindingRepository.findByDeviceSn("SN-NOTEXIST"))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/h5/devices/SN-NOTEXIST/contract"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.message").value("未找到该设备关联合同"));
    }
}
