package com.sanshuiyuan.asset.api;

import com.sanshuiyuan.asset.domain.Sku;
import com.sanshuiyuan.asset.domain.SkuStatus;
import com.sanshuiyuan.asset.infra.repository.SkuRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * C.1.4: SkuController integration tests covering list (ACTIVE) / detail exists /
 * detail not found (404) / detail of an inactive SKU (404).
 *
 * Security note: the production SecurityConfig registers a custom JwtBearerFilter via a
 * SecurityFilterChain bean, which @WebMvcTest does not import. A permissive TestSecurityConfig
 * is imported so the auto-configured default chain does not return 401. SkuController has no
 * @AuthenticationPrincipal dependency.
 */
@WebMvcTest(SkuController.class)
@Import(TestSecurityConfig.class)
class SkuControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    SkuRepository skuRepository;

    private Sku sku(Long id, String name) {
        Sku s = new Sku();
        s.setId(id);
        s.setName(name);
        s.setPriceCents(19900L);
        s.setStatus(SkuStatus.ACTIVE);
        return s;
    }

    @Test
    void listSkus_returnsActiveSkus() throws Exception {
        when(skuRepository.findByStatus(SkuStatus.ACTIVE))
                .thenReturn(List.of(sku(1L, "基础饮水机"), sku(2L, "高级净水器")));

        mockMvc.perform(get("/skus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("基础饮水机"));
    }

    @Test
    void getSku_existsActive_returnsSku() throws Exception {
        when(skuRepository.findByIdAndStatus(eq(1L), eq(SkuStatus.ACTIVE)))
                .thenReturn(Optional.of(sku(1L, "基础饮水机")));

        mockMvc.perform(get("/skus/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getSku_notFound_returns404() throws Exception {
        when(skuRepository.findByIdAndStatus(eq(99L), eq(SkuStatus.ACTIVE)))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/skus/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getSku_inactiveSku_returns404() throws Exception {
        // An inactive SKU is never returned by findByIdAndStatus(.., ACTIVE), so the
        // controller yields 404 even though the row exists.
        when(skuRepository.findByIdAndStatus(eq(5L), eq(SkuStatus.ACTIVE)))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/skus/5"))
                .andExpect(status().isNotFound());
    }
}
