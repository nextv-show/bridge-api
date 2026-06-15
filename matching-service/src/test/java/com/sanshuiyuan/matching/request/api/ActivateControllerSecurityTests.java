package com.sanshuiyuan.matching.request.api;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sanshuiyuan.matching.config.SecurityConfig;
import com.sanshuiyuan.matching.request.api.dto.ActivateResponse;
import com.sanshuiyuan.matching.request.application.ActivateDeviceUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 029 / codex P1：/internal/matching/activate 必须仅限 S2S。守护回归——普通 C 端 H5 JWT（被
 * H5JwtFilter 认证为 NO_AUTHORITIES）不得访问 /internal/** 设备状态推进端点，否则用户可猜 SN 越权激活。
 * 导入真实 SecurityConfig 走完整过滤链（standaloneSetup wire 测试绕过了安全，故另立此测试）。
 */
@WebMvcTest(ActivateController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "h5.jwt-secret=test-h5-jwt-secret-please-override-in-prod-0001",
        "s2s.token=test-s2s-token"
})
class ActivateControllerSecurityTests {

    private static final String H5_SECRET = "test-h5-jwt-secret-please-override-in-prod-0001";
    private static final String S2S = "test-s2s-token";

    @Autowired
    MockMvc mockMvc;

    @MockBean
    ActivateDeviceUseCase useCase;

    private static final String BODY = "{\"sn\":\"SN-SEC-1\"}";

    @Test
    void s2sBearer_allowed() throws Exception {
        when(useCase.activate("SN-SEC-1")).thenReturn(new ActivateResponse("SN-SEC-1", true));

        mockMvc.perform(post("/internal/matching/activate")
                        .header("Authorization", "Bearer " + S2S)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());

        verify(useCase).activate(eq("SN-SEC-1"));
    }

    @Test
    void h5Jwt_forbidden() throws Exception {
        mockMvc.perform(post("/internal/matching/activate")
                        .header("Authorization", "Bearer " + mintH5Jwt("c-end-user-openid"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());

        verify(useCase, never()).activate(any());
    }

    @Test
    void noAuth_forbidden() throws Exception {
        mockMvc.perform(post("/internal/matching/activate")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());

        verify(useCase, never()).activate(any());
    }

    /** 用与服务端同密钥签发的合法 H5 JWT（模拟真实 C 端登录态）。 */
    private static String mintH5Jwt(String subject) throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader(JWSAlgorithm.HS256),
                new JWTClaimsSet.Builder()
                        .subject(subject)
                        .expirationTime(new Date(System.currentTimeMillis() + 3_600_000))
                        .build());
        jwt.sign(new MACSigner(H5_SECRET.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
