package com.sanshuiyuan.h5.api;

import com.sanshuiyuan.h5.AbstractMysqlContainerTest;
import com.sanshuiyuan.h5.config.CacheConfig;
import com.sanshuiyuan.h5.domain.ConfigStatus;
import com.sanshuiyuan.h5.infra.repository.LandingConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B.2.5：GET /api/h5/landing/config 返回 PUBLISHED seed；缓存命中第二次不打库。
 */
@SpringBootTest
@AutoConfigureMockMvc
class LandingControllerIT extends AbstractMysqlContainerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    CacheManager cacheManager;

    @SpyBean
    LandingConfigRepository configRepo;

    @BeforeEach
    void clearCaffeine() {
        Cache c = cacheManager.getCache(CacheConfig.LANDING_CONFIG_CACHE);
        if (c != null) {
            c.clear();
        }
    }

    @Test
    void getConfig_returnsPublishedSeed() throws Exception {
        mockMvc.perform(get("/api/h5/landing/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.hero.titleLines[0]").value("让一台水机"))
                .andExpect(jsonPath("$.data.hero.kpis.length()").value(3))
                .andExpect(jsonPath("$.data.features.length()").value(4))
                .andExpect(jsonPath("$.data.features[0].iconKey").value("water-return"))
                .andExpect(jsonPath("$.data.simulator.baseRateBp").value(850))
                .andExpect(jsonPath("$.data.simulator.defaultLiters").value(46))
                .andExpect(jsonPath("$.data.trustBadges.length()").value(4))
                .andExpect(jsonPath("$.data.footer.icpNumber").exists());
    }

    @Test
    void getConfig_secondCall_hitsCache_doesNotQueryDb() throws Exception {
        mockMvc.perform(get("/api/h5/landing/config")).andExpect(status().isOk());
        mockMvc.perform(get("/api/h5/landing/config")).andExpect(status().isOk());

        // Redis 在测试中不可用 → 仅 Caffeine L1 命中；DB 查询只应发生一次。
        verify(configRepo, times(1))
                .findFirstByStatusOrderByPublishedAtDesc(ConfigStatus.PUBLISHED);
    }
}
