package com.sanshuiyuan.ess.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanshuiyuan.ess.config.SecurityConfig;
import com.sanshuiyuan.ess.service.EssCallbackService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EssCallbackController.class)
@Import(SecurityConfig.class)
@ExtendWith(MockitoExtension.class)
class EssCallbackControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EssCallbackService callbackService;

    @Test
    void handleCallback_success_shouldReturnOk() throws Exception {
        var result = EssCallbackService.CallbackResult.success("c-001", "flow-001", "FlowFinished");
        when(callbackService.handleCallback(any(), any(), any())).thenReturn(result);

        mockMvc.perform(post("/api/internal/ess/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"FlowId\":\"flow-001\",\"EventType\":\"FlowFinished\"}")
                        .header("X-ESS-Signature", "test-sig")
                        .header("X-ESS-Timestamp", "1700000000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.contractId").value("c-001"));
    }

    @Test
    void handleCallback_ignored_shouldReturnOk() throws Exception {
        var result = EssCallbackService.CallbackResult.ignored("缺少 FlowId");
        when(callbackService.handleCallback(any(), any(), any())).thenReturn(result);

        mockMvc.perform(post("/api/internal/ess/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"EventType\":\"Test\"}")
                        .header("X-ESS-Signature", "test-sig")
                        .header("X-ESS-Timestamp", "1700000000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("Ignored: 缺少 FlowId"));
    }

    @Test
    void handleCallback_error_shouldReturnOkWithErrorCode() throws Exception {
        when(callbackService.handleCallback(any(), any(), any()))
                .thenThrow(new RuntimeException("Something went wrong"));

        mockMvc.perform(post("/api/internal/ess/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("X-ESS-Signature", "bad-sig")
                        .header("X-ESS-Timestamp", "1700000000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-1));
    }
}
