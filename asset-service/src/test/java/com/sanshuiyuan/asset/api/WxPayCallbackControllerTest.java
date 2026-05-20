package com.sanshuiyuan.asset.api;

import com.sanshuiyuan.asset.application.PayCallbackUseCase;
import com.sanshuiyuan.asset.infra.wxpay.VerifiedCallback;
import com.sanshuiyuan.asset.infra.wxpay.WxPayCallbackVerifier;
import com.sanshuiyuan.asset.infra.wxpay.WxPaySignatureException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D.2.5 (signature-error branch): the /wxpay/callback endpoint delegates verification to the
 * {@link WxPayCallbackVerifier} seam. A verification failure surfaces as HTTP 401 + FAIL; a
 * successful verification drives {@link PayCallbackUseCase#handleCallback} and returns SUCCESS.
 *
 * Security note: /wxpay/callback is on the production permit-all whitelist; the permissive
 * TestSecurityConfig keeps this slice from requiring auth.
 */
@WebMvcTest(WxPayCallbackController.class)
@Import(TestSecurityConfig.class)
class WxPayCallbackControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    PayCallbackUseCase payCallbackUseCase;

    @MockBean
    WxPayCallbackVerifier verifier;

    @Test
    void callback_signatureFailure_returnsFail401() throws Exception {
        when(verifier.verify(anyMap(), anyString()))
                .thenThrow(new WxPaySignatureException("signature verification failed"));

        mockMvc.perform(post("/wxpay/callback")
                        .header("Wechatpay-Signature", "bad")
                        .header("Wechatpay-Serial", "serial")
                        .header("Wechatpay-Nonce", "nonce")
                        .header("Wechatpay-Timestamp", "123")
                        .contentType("application/json")
                        .content("{\"resource\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("FAIL"))
                .andExpect(jsonPath("$.message").value("签名验证失败"));

        verify(payCallbackUseCase, never()).handleCallback(anyString(), any(), anyString());
    }

    @Test
    void callback_verifiedSuccessfully_handlesAndReturnsSuccess() throws Exception {
        when(verifier.verify(anyMap(), anyString()))
                .thenReturn(new VerifiedCallback("txn-ok-1", 555L));

        mockMvc.perform(post("/wxpay/callback")
                        .header("Wechatpay-Signature", "good")
                        .header("Wechatpay-Serial", "serial")
                        .header("Wechatpay-Nonce", "nonce")
                        .header("Wechatpay-Timestamp", "123")
                        .contentType("application/json")
                        .content("{\"resource\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("成功"));

        verify(payCallbackUseCase, times(1))
                .handleCallback(eq("txn-ok-1"), eq(555L), anyString());
    }
}
