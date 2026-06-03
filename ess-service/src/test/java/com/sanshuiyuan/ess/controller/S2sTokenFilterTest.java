package com.sanshuiyuan.ess.controller;

import com.sanshuiyuan.ess.config.SecurityConfig;
import com.sanshuiyuan.ess.domain.Contract;
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
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * spec 006 Phase A：/api/admin/* 的 S2S 鉴权（{@link com.sanshuiyuan.ess.auth.S2sTokenFilter}）。
 * <p>
 * 缺 / 错 X-S2S-Token → 401；带正确 token → 放行（200）。
 */
@WebMvcTest(ContractAdminController.class)
@Import(SecurityConfig.class)
@ExtendWith(MockitoExtension.class)
class S2sTokenFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private com.sanshuiyuan.ess.service.ContractQueryService queryService;
    @MockBean
    private com.sanshuiyuan.ess.service.ContractAccessLogService accessLogService;
    @MockBean
    private com.sanshuiyuan.ess.service.ContractArchiveService archiveService;
    @MockBean
    private com.sanshuiyuan.ess.service.CertificateService certificateService;
    @MockBean
    private com.sanshuiyuan.ess.service.AuditTrailService auditTrailService;
    @MockBean
    private com.sanshuiyuan.ess.infra.repository.ContractRepository contractRepository;
    @MockBean
    private com.sanshuiyuan.ess.service.ReconcileSigningContractsJob reconcileJob;
    @MockBean
    private com.sanshuiyuan.ess.service.ContractCompletionBridge completionBridge;
    @MockBean
    private com.sanshuiyuan.ess.service.EssContractService essContractService;

    @Test
    void adminEndpoint_withoutS2sToken_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/contracts").param("page", "0").param("size", "20"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpoint_withWrongS2sToken_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/contracts").header("X-S2S-Token", "WRONG"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpoint_withCorrectS2sToken_isAllowed() throws Exception {
        Contract contract = Contract.createDraft("CT-S2S-001", 1L, 100L, "ORD-S2S", "SN-S2S");
        Page<Contract> page = new PageImpl<>(List.of(contract), PageRequest.of(0, 20), 1);
        when(queryService.searchContracts(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/admin/contracts")
                        .header("X-S2S-Token", "local-dev-static-token")
                        .param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
