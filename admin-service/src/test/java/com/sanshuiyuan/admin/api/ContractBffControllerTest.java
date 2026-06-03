package com.sanshuiyuan.admin.api;

import com.sanshuiyuan.admin.config.SecurityConfig;
import com.sanshuiyuan.admin.infra.client.EssContractClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * spec 006 Phase A：合同 BFF 鉴权（/admin/contracts/*）。
 * <p>
 * 无 admin token → 401（AdminJwtFilter + anyRequest authenticated）；带合法 admin token → 放行并委托下游。
 */
@WebMvcTest(ContractBffController.class)
@Import(SecurityConfig.class)
class ContractBffControllerTest {

    /** 与 application.yml 默认 admin.jwt-secret 一致。 */
    private static final String ADMIN_SECRET = "dev-admin-secret-key-change-in-prod";
    private static final SecurityConfig.AdminJwtUtil TOKEN_FACTORY =
            new SecurityConfig.AdminJwtUtil(ADMIN_SECRET, 8);

    @Autowired
    MockMvc mockMvc;

    @MockBean
    EssContractClient essContractClient;

    @Test
    void list_withoutAdminToken_returns401() throws Exception {
        mockMvc.perform(get("/admin/contracts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_withAdminToken_delegatesToEss() throws Exception {
        when(essContractClient.list(org.mockito.ArgumentMatchers.any()))
                .thenReturn(ResponseEntity.ok(Map.of("code", 0, "total", 1)));

        String token = TOKEN_FACTORY.generateToken(1L, "admin", "SUPER_ADMIN");
        mockMvc.perform(get("/admin/contracts").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    // ========== Phase E：失败重试委托 ==========

    @Test
    void retryArchive_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/admin/contracts/7/retry-archive"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void retryArchive_withToken_delegatesToEss() throws Exception {
        when(essContractClient.retryArchive(7L))
                .thenReturn(ResponseEntity.ok(Map.of("code", 0, "success", true)));

        String token = TOKEN_FACTORY.generateToken(1L, "admin", "SUPER_ADMIN");
        mockMvc.perform(post("/admin/contracts/7/retry-archive").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void retryCertificate_withToken_delegatesToEss() throws Exception {
        when(essContractClient.retryCertificate(7L))
                .thenReturn(ResponseEntity.ok(Map.of("code", 0, "certificateNo", "CERT-7")));

        String token = TOKEN_FACTORY.generateToken(1L, "admin", "SUPER_ADMIN");
        mockMvc.perform(post("/admin/contracts/7/retry-certificate").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.certificateNo").value("CERT-7"));
    }
}
