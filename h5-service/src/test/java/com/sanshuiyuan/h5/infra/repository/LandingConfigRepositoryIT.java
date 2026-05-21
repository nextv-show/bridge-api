package com.sanshuiyuan.h5.infra.repository;

import com.sanshuiyuan.h5.AbstractMysqlContainerTest;
import com.sanshuiyuan.h5.domain.ConfigStatus;
import com.sanshuiyuan.h5.domain.LandingConfig;
import com.sanshuiyuan.h5.domain.LandingFeature;
import com.sanshuiyuan.h5.domain.LandingTrustBadge;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B.1.4：seed（V004）落库后能查到唯一 PUBLISHED + 子项按 sort 排序。
 * 顺带验证 JSON 列（hero/simulator/footer）与 Hibernate validate（ddl-auto=validate）相容。
 */
@SpringBootTest
class LandingConfigRepositoryIT extends AbstractMysqlContainerTest {

    @Autowired
    LandingConfigRepository configRepo;

    @Autowired
    LandingFeatureRepository featureRepo;

    @Autowired
    LandingTrustBadgeRepository badgeRepo;

    @Test
    void seed_hasOnePublishedConfig_withSortedChildren() {
        Optional<LandingConfig> published =
                configRepo.findFirstByStatusOrderByPublishedAtDesc(ConfigStatus.PUBLISHED);

        assertThat(published).isPresent();
        LandingConfig cfg = published.get();
        assertThat(cfg.getStatus()).isEqualTo(ConfigStatus.PUBLISHED);
        assertThat(cfg.getHeroJson()).contains("让一台水机");
        assertThat(cfg.getSimulatorJson()).contains("baseRateBp");
        assertThat(cfg.getFooterJson()).contains("icpNumber");

        List<LandingFeature> features = featureRepo.findByConfigIdOrderBySortAsc(cfg.getId());
        assertThat(features).hasSize(4);
        assertThat(features).extracting(LandingFeature::getIconKey)
                .containsExactly("water-return", "self-check", "rebate", "cooldown");

        List<LandingTrustBadge> badges = badgeRepo.findByConfigIdOrderBySortAsc(cfg.getId());
        assertThat(badges).hasSize(4);
        assertThat(badges).extracting(LandingTrustBadge::getTitle)
                .containsExactly("唯一 SN 码", "13% 增值税专票", "24h 冷静期", "第三方资金托管");
    }
}
