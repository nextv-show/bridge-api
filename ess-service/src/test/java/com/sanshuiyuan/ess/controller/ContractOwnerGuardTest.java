package com.sanshuiyuan.ess.controller;

import com.sanshuiyuan.ess.auth.ContractOwnershipGuard;
import com.sanshuiyuan.ess.auth.H5JwtService;
import com.sanshuiyuan.ess.config.SecurityConfig;
import com.sanshuiyuan.ess.domain.Contract;
import com.sanshuiyuan.ess.infra.client.UserServiceClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * spec 006 Phase A：H5 合同 owner 校验（{@link ContractOwnershipGuard}）。
 * <p>
 * 用真实 guard + 真实 H5JwtFilter（SecurityConfig），mock {@link UserServiceClient}：
 * <ul>
 *   <li>无 H5 token → 401（CurrentOpenid.require）。</li>
 *   <li>有 token 但 openid 解析出的 userId 与合同 userId 不一致 → 403。</li>
 *   <li>有 token 且属主一致 → 放行（200）。</li>
 * </ul>
 * 这里用真实 {@code ContractViewController.GET /contracts/{id}/view} 作为受保护端点样本。
 */
@WebMvcTest(ContractViewController.class)
@Import({SecurityConfig.class, ContractOwnershipGuard.class})
@ExtendWith(MockitoExtension.class)
class ContractOwnerGuardTest {

    /** 与 application.yml 默认 h5.jwt-secret 一致，用于在测试内签发合法 token。 */
    private static final String H5_SECRET = "dev-h5-jwt-secret-please-override-in-prod-0001";
    private static final H5JwtService TOKEN_FACTORY = new H5JwtService(H5_SECRET, 72);

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private com.sanshuiyuan.ess.service.ContractArchiveService archiveService;
    @MockBean
    private com.sanshuiyuan.ess.service.ContractQueryService queryService;
    @MockBean
    private com.sanshuiyuan.ess.service.ContractAccessLogService accessLogService;
    @MockBean
    private com.sanshuiyuan.ess.infra.repository.ContractSnBindingRepository snBindingRepository;
    @MockBean
    private com.sanshuiyuan.ess.service.AuditTrailService auditTrailService;
    @MockBean
    private com.sanshuiyuan.ess.service.CrossPlatformConsistencyService consistencyService;

    // 真实 ContractOwnershipGuard 依赖此 client；mock 其解析结果模拟属主/非属主。
    @MockBean
    private UserServiceClient userServiceClient;

    @Test
    void view_withoutToken_returns401() throws Exception {
        Contract contract = Contract.createDraft("CT-OWNER-401", 1L, 100L, "ORD-401", "SN-401");
        when(queryService.getContractDetail(1L)).thenReturn(contract);
        // 无 Authorization 头：H5JwtFilter 不注入身份，assertOwner→CurrentOpenid.require()→401。
        mockMvc.perform(get("/api/h5/contracts/1/view"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void view_withoutToken_nonexistentContract_returns401NotError() throws Exception {
        // issue #35：鉴权应先于存在性校验。无 token 访问"不存在"的合同也应 401（而非 404/500），
        // 不暴露合同是否存在。getContractDetail 抛错；若鉴权在加载之后，会先抛错暴露存在性。
        when(queryService.getContractDetail(404L))
                .thenThrow(new IllegalArgumentException("合同不存在: id=404"));
        mockMvc.perform(get("/api/h5/contracts/404/view"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void view_byNonOwner_returns403() throws Exception {
        Contract contract = Contract.createDraft("CT-OWNER-001", 1L, 100L, "ORD-OWN", "SN-OWN");
        when(queryService.getContractDetail(1L)).thenReturn(contract); // 合同归属 userId=100
        when(userServiceClient.resolveUserId("openid-other")).thenReturn(999L); // 会话是别人

        String token = TOKEN_FACTORY.generate("openid-other");
        mockMvc.perform(get("/api/h5/contracts/1/view").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void view_byOwner_isAllowed() throws Exception {
        Contract contract = Contract.createDraft("CT-OWNER-002", 1L, 100L, "ORD-OWN2", "SN-OWN2");
        when(queryService.getContractDetail(2L)).thenReturn(contract); // 归属 userId=100
        when(userServiceClient.resolveUserId("openid-owner")).thenReturn(100L); // 会话即属主

        var viewResult = new com.sanshuiyuan.ess.service.CrossPlatformConsistencyService.ContractViewResult(
                2L, "CT-OWNER-002", "ARCHIVED", "",
                "https://oss.sanshuiyuan.com/c.pdf", "h", true, "");
        when(consistencyService.getUnifiedContractView(2L)).thenReturn(viewResult);
        when(accessLogService.logAccess(org.mockito.ArgumentMatchers.eq(2L),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(com.sanshuiyuan.ess.domain.ContractAccessLog.create(2L, null,
                        com.sanshuiyuan.ess.domain.ContractAccessLog.AccessType.VIEW,
                        com.sanshuiyuan.ess.domain.ContractAccessLog.AccessSource.H5, "127.0.0.1", "test"));

        String token = TOKEN_FACTORY.generate("openid-owner");
        mockMvc.perform(get("/api/h5/contracts/2/view").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
