package com.sanshuiyuan.h5.application;

import com.sanshuiyuan.h5.AbstractMysqlContainerTest;
import com.sanshuiyuan.h5.api.dto.LandingConfigResponse;
import com.sanshuiyuan.h5.config.CacheConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C.4：向 DB 注入一条含「收益率」的 PUBLISHED 配置 → 接口出口合规校验拦截 → 回退上一份合规快照
 * （不外泄违禁文案）。证明监管「双道防线」之第二道（出口）在运营误配时生效。
 */
@SpringBootTest
class LandingComplianceFallbackIT extends AbstractMysqlContainerTest {

    @Autowired
    LandingConfigService service;

    @Autowired
    CacheManager cacheManager;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clearCaffeine() {
        Cache c = cacheManager.getCache(CacheConfig.LANDING_CONFIG_CACHE);
        if (c != null) {
            c.clear();
        }
    }

    @AfterEach
    void cleanupBadConfig() {
        jdbc.update("DELETE FROM landing_config WHERE updated_by = 'test-bad'");
    }

    @Test
    void nonCompliantPublishedConfig_fallsBackToLastGoodSnapshot() {
        // 1) 先正常读一次 → 合规 seed 成为「上一份合规快照」。
        LandingConfigResponse good = service.getPublishedConfig();
        assertThat(good.version()).isEqualTo(1);
        assertThat(good.footer().disclaimer()).doesNotContain("收益率");

        // 2) 注入一条更晚 published_at、含违禁词「收益率」的 PUBLISHED 配置（模拟运营误配）。
        String hero = "{\"logoUrl\":\"/x\",\"titleLines\":[\"测试\"],\"subtitle\":\"测试\",\"kpis\":[],\"industries\":[]}";
        String sim = "{\"minLiters\":0,\"maxLiters\":50,\"defaultLiters\":10,\"baseRateBp\":850,"
                + "\"networkBonusBp\":400,\"bonusThresholdLiters\":12,\"unit\":\"升\",\"outputLabel\":\"返利\",\"disclaimer\":\"提示\"}";
        String footer = "{\"disclaimer\":\"预期年化收益率高达 20%，稳赚保本\",\"icpNumber\":\"x\"}";
        jdbc.update("INSERT INTO landing_config(version,status,hero_json,simulator_json,footer_json,updated_by,published_at) "
                        + "VALUES (?,?,?,?,?,?, DATE_ADD(NOW(), INTERVAL 1 DAY))",
                99, "PUBLISHED", hero, sim, footer, "test-bad");

        // 3) 清 L1 缓存后再读 → DB 返回违规配置，出口校验拦截并回退合规快照。
        Cache c = cacheManager.getCache(CacheConfig.LANDING_CONFIG_CACHE);
        if (c != null) {
            c.clear();
        }
        LandingConfigResponse afterBad = service.getPublishedConfig();

        // 返回的是上一份合规快照（version=1），而非违规的 version=99；C 端零违禁词外泄。
        assertThat(afterBad.version()).isEqualTo(1);
        assertThat(afterBad.footer().disclaimer()).doesNotContain("收益率");
    }
}
