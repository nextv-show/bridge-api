package com.sanshuiyuan.asset.api;

import com.sanshuiyuan.asset.application.AssetQueryService;
import com.sanshuiyuan.asset.domain.DeviceAsset;
import com.sanshuiyuan.asset.domain.Stage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * D.3.x: /assets/mine 分页列表 DTO 映射、/assets/{sn} 归属校验。
 * Security 同 OrderControllerTest：permit-all 测试链 + authentication() 注入 Long principal。
 */
@WebMvcTest(AssetController.class)
@Import(TestSecurityConfig.class)
class AssetControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AssetQueryService assetQueryService;

    private RequestPostProcessor principal(Long userId) {
        return authentication(new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private DeviceAsset asset(Long id, Long userId, String sn, Stage stage, long incomeCents, int roiBp) {
        DeviceAsset a = new DeviceAsset();
        a.setId(id);
        a.setUserId(userId);
        a.setSn(sn);
        a.setModel("S-3000");
        a.setPurchasedAt(LocalDateTime.now());
        a.setStage(stage);
        a.setCumulativeIncomeCents(incomeCents);
        a.setRoiBp(roiBp);
        return a;
    }

    @Test
    void getMyAssets_mapsDtoFlags() throws Exception {
        DeviceAsset pending = asset(1L, 7L, null, Stage.PENDING_MATCH, 0L, 0);
        DeviceAsset fused = asset(2L, 7L, "SN-2", Stage.FUSED, 500000L, 20000);
        when(assetQueryService.getMyAssets(eq(7L), eq(0), eq(50)))
                .thenReturn(new PageImpl<>(List.of(pending, fused)));

        mockMvc.perform(get("/assets/mine").with(principal(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // FR-5.3: 待撮合行隐藏收益字段
                .andExpect(jsonPath("$[0].pendingMatch").value(true))
                .andExpect(jsonPath("$[0].fused").value(false))
                .andExpect(jsonPath("$[0].cumulativeIncomeCents").isEmpty())
                .andExpect(jsonPath("$[0].roiBp").isEmpty())
                // 已熔断行正常下发收益字段
                .andExpect(jsonPath("$[1].pendingMatch").value(false))
                .andExpect(jsonPath("$[1].fused").value(true))
                .andExpect(jsonPath("$[1].cumulativeIncomeCents").value(500000))
                .andExpect(jsonPath("$[1].roiBp").value(20000));
    }

    @Test
    void getMyAssets_passesPageAndSizeThrough() throws Exception {
        when(assetQueryService.getMyAssets(eq(7L), eq(2), eq(10_000)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/assets/mine").param("page", "2").param("size", "10000").with(principal(7L)))
                .andExpect(status().isOk());
        // service is responsible for clamping size; controller passes the raw request through
    }

    @Test
    void getAssetBySn_owned_returns200() throws Exception {
        when(assetQueryService.getOwnedAsset(eq(7L), eq("SN-2")))
                .thenReturn(Optional.of(asset(2L, 7L, "SN-2", Stage.STAGE_1, 1000L, 1500)));

        mockMvc.perform(get("/assets/SN-2").with(principal(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sn").value("SN-2"));
    }

    @Test
    void getAssetBySn_notOwnedOrMissing_returns404() throws Exception {
        when(assetQueryService.getOwnedAsset(eq(9L), eq("SN-2"))).thenReturn(Optional.empty());

        mockMvc.perform(get("/assets/SN-2").with(principal(9L)))
                .andExpect(status().isNotFound());
    }
}
