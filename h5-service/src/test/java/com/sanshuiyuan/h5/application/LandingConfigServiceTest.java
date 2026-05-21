package com.sanshuiyuan.h5.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanshuiyuan.h5.application.compliance.ComplianceTextValidator;
import com.sanshuiyuan.h5.common.BizException;
import com.sanshuiyuan.h5.common.ErrorCode;
import com.sanshuiyuan.h5.domain.ConfigStatus;
import com.sanshuiyuan.h5.infra.repository.LandingConfigRepository;
import com.sanshuiyuan.h5.infra.repository.LandingFeatureRepository;
import com.sanshuiyuan.h5.infra.repository.LandingTrustBadgeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 纯单测：无 PUBLISHED 配置时 assembleFromDb 抛 503 NO_ACTIVE_CONFIG（前端据此回退默认）。
 */
@ExtendWith(MockitoExtension.class)
class LandingConfigServiceTest {

    @Mock
    LandingConfigRepository configRepo;
    @Mock
    LandingFeatureRepository featureRepo;
    @Mock
    LandingTrustBadgeRepository badgeRepo;

    @Test
    void assembleFromDb_noPublished_throwsNoActiveConfig() {
        when(configRepo.findFirstByStatusOrderByPublishedAtDesc(ConfigStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        LandingConfigService service = new LandingConfigService(
                configRepo, featureRepo, badgeRepo, new ObjectMapper(), null, null,
                new ComplianceTextValidator());

        assertThatThrownBy(service::assembleFromDb)
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).errorCode())
                .isEqualTo(ErrorCode.NO_ACTIVE_CONFIG);
    }
}
