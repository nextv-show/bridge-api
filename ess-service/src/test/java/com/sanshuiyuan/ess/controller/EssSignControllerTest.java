package com.sanshuiyuan.ess.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanshuiyuan.ess.config.SecurityConfig;
import com.sanshuiyuan.ess.service.EssSignService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EssSignController.class)
@Import(SecurityConfig.class)
@ExtendWith(MockitoExtension.class)
class EssSignControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EssSignService signService;

    private static final String MOCK_H5_URL = "https://ess.qq.com/h5/sign?token=mock-token";
    private static final String MOCK_EMBED_URL = "https://ess.qq.com/h5/embed?token=mock-token";
    private static final String MOCK_APP_URL = "https://ess.qq.com/app?token=mock-token";

    // ========== T014: H5 ==========

    @Test
    void generateH5SignUrl_shouldReturnSignUrl() throws Exception {
        when(signService.generateH5SignUrl("c-001", "signer-001", "https://jump.url", "jump"))
                .thenReturn(MOCK_H5_URL);

        mockMvc.perform(post("/api/ess/sign/h5-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contractId\":\"c-001\",\"signerId\":\"signer-001\",\"jumpUrl\":\"https://jump.url\",\"h5Type\":\"jump\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.signUrl").value(MOCK_H5_URL))
                .andExpect(jsonPath("$.h5Type").value("jump"));
    }

    @Test
    void generateH5SignUrl_embedMode_shouldReturnEmbedUrl() throws Exception {
        when(signService.generateH5SignUrl("c-001", "signer-001", "", "embed"))
                .thenReturn(MOCK_EMBED_URL);

        mockMvc.perform(post("/api/ess/sign/h5-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contractId\":\"c-001\",\"signerId\":\"signer-001\",\"h5Type\":\"embed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.h5Type").value("embed"));
    }

    @Test
    void generateH5SignUrl_missingContractId_shouldReturnError() throws Exception {
        mockMvc.perform(post("/api/ess/sign/h5-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signerId\":\"signer-001\"}"))
                .andExpect(status().isBadRequest());
    }

    // ========== T015: miniapp ==========

    @Test
    void generateMiniAppSignParams_shouldReturnParams() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode mockParams = mapper.readTree("{\"MiniAppPath\":\"/pages/sign\",\"Token\":\"abc123\"}");
        when(signService.generateMiniAppSignParams("c-001", "signer-001", "wx123456"))
                .thenReturn(mockParams);

        mockMvc.perform(post("/api/ess/sign/miniapp-params")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contractId\":\"c-001\",\"signerId\":\"signer-001\",\"wxAppId\":\"wx123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.signParams.MiniAppPath").value("/pages/sign"));
    }

    // ========== T016: app ==========

    @Test
    void generateAppSignParams_android_shouldReturnParams() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode mockParams = mapper.readTree("{\"AppSignUrl\":\"" + MOCK_APP_URL + "\"}");
        when(signService.generateAppSignParams("c-001", "signer-001", "android"))
                .thenReturn(mockParams);

        mockMvc.perform(post("/api/ess/sign/app-params")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contractId\":\"c-001\",\"signerId\":\"signer-001\",\"appType\":\"android\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.signParams.AppSignUrl").value(MOCK_APP_URL));
    }

    @Test
    void generateAppSignParams_ios_shouldReturnParams() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode mockParams = mapper.readTree("{\"AppSignUrl\":\"" + MOCK_APP_URL + "\"}");
        when(signService.generateAppSignParams("c-002", "signer-002", "ios"))
                .thenReturn(mockParams);

        mockMvc.perform(post("/api/ess/sign/app-params")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contractId\":\"c-002\",\"signerId\":\"signer-002\",\"appType\":\"ios\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.signParams.AppSignUrl").value(MOCK_APP_URL));
    }

    @Test
    void generateAppSignParams_missingAppType_shouldFail() throws Exception {
        mockMvc.perform(post("/api/ess/sign/app-params")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contractId\":\"c-001\",\"signerId\":\"signer-001\"}"))
                .andExpect(status().isBadRequest());
    }

    // ========== Server Sign ==========

    @Test
    void createServerSign_shouldReturnOk() throws Exception {
        mockMvc.perform(post("/api/ess/sign/server-sign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contractId\":\"c-001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("企业自动签章完成"));
    }
}
