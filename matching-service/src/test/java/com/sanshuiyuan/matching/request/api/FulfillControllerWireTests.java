package com.sanshuiyuan.matching.request.api;

import com.sanshuiyuan.matching.request.application.FulfillUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D.4 fulfill 端点 wire 层回归（standaloneSetup，使用默认 ObjectMapper —— 与 matching-service
 * 无全局 SNAKE_CASE 策略一致，故能真实复现 JSON 边界）。
 *
 * <p>守护历史 bug：logistics InstalledEventPublisher 发送 snake_case
 * (request_id/device_asset_id/logistics_order_id)，而 FulfillBody 字段为 camelCase。
 * 若 FulfillBody 缺 {@code @JsonProperty}，snake_case 不绑定 → requestId=null、deviceAssetId=0，
 * MATCHING 履约被误判为 SELF_USE 路径并 409。本测试断言两条路径都按线格式正确绑定。
 */
class FulfillControllerWireTests {

    private FulfillUseCase fulfillUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        fulfillUseCase = mock(FulfillUseCase.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new FulfillController(fulfillUseCase)).build();
    }

    /** MATCHING 路径：snake_case 非空 request_id 必须绑定到 requestId（回归核心）。 */
    @Test
    void matchingPayload_snakeCase_bindsAllFields() throws Exception {
        mockMvc.perform(post("/internal/matching/fulfill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request_id\":3001,\"device_asset_id\":2001,\"logistics_order_id\":1}"))
                .andExpect(status().isOk());

        // 修复前：requestId 会绑成 null、deviceAssetId 绑成 0（误入 SELF_USE）。
        verify(fulfillUseCase).fulfill(eq(3001L), eq(2001L), eq(1L));
    }

    /** SELF_USE 路径：request_id 缺省时 requestId 必须为 null，仍正确路由到 SELF_USE 分支。 */
    @Test
    void selfUsePayload_missingRequestId_bindsNull() throws Exception {
        mockMvc.perform(post("/internal/matching/fulfill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"device_asset_id\":2002,\"logistics_order_id\":2}"))
                .andExpect(status().isOk());

        verify(fulfillUseCase).fulfill(isNull(), eq(2002L), eq(2L));
    }

    /** SELF_USE 路径：显式 request_id=null 同样绑定为 null。 */
    @Test
    void selfUsePayload_explicitNullRequestId_bindsNull() throws Exception {
        mockMvc.perform(post("/internal/matching/fulfill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request_id\":null,\"device_asset_id\":2003,\"logistics_order_id\":3}"))
                .andExpect(status().isOk());

        verify(fulfillUseCase).fulfill(isNull(), eq(2003L), eq(3L));
    }
}
