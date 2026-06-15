package com.sanshuiyuan.matching.request.api;

import com.sanshuiyuan.matching.request.api.dto.ActivateResponse;
import com.sanshuiyuan.matching.request.application.ActivateDeviceUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 029 activate 端点 wire 层（standaloneSetup + 默认 ObjectMapper，与 matching 无全局 SNAKE_CASE 一致）。
 * 守护：iot-gateway 发 snake_case {"sn":"..."} 必须绑定到 ActivateBody.sn；响应回 snake_case sn/activated。
 * （防 #98 同类：record 字段无 @JsonProperty 时 snake_case 不绑定。）
 */
class ActivateControllerWireTests {

    private ActivateDeviceUseCase useCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        useCase = mock(ActivateDeviceUseCase.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ActivateController(useCase)).build();
    }

    @Test
    void snakeCaseSn_bindsAndActivates() throws Exception {
        when(useCase.activate("SN-E2E-001")).thenReturn(new ActivateResponse("SN-E2E-001", true));

        mockMvc.perform(post("/internal/matching/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sn\":\"SN-E2E-001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sn").value("SN-E2E-001"))
                .andExpect(jsonPath("$.activated").value(true));

        verify(useCase).activate(eq("SN-E2E-001"));
    }

    @Test
    void idempotentNoop_returnsActivatedFalse() throws Exception {
        when(useCase.activate("SN-X")).thenReturn(new ActivateResponse("SN-X", false));

        mockMvc.perform(post("/internal/matching/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sn\":\"SN-X\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activated").value(false));
    }
}
