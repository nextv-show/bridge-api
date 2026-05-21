package com.sanshuiyuan.h5.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanshuiyuan.h5.api.dto.FeatureDto;
import com.sanshuiyuan.h5.api.dto.FooterDto;
import com.sanshuiyuan.h5.api.dto.HeroDto;
import com.sanshuiyuan.h5.api.dto.LandingConfigResponse;
import com.sanshuiyuan.h5.api.dto.SimulatorDto;
import com.sanshuiyuan.h5.api.dto.TrustBadgeDto;
import com.sanshuiyuan.h5.application.compliance.ComplianceTextValidator;
import com.sanshuiyuan.h5.common.BizException;
import com.sanshuiyuan.h5.common.ComplianceException;
import com.sanshuiyuan.h5.common.ErrorCode;
import com.sanshuiyuan.h5.config.CacheConfig;
import com.sanshuiyuan.h5.domain.ConfigStatus;
import com.sanshuiyuan.h5.domain.LandingConfig;
import com.sanshuiyuan.h5.infra.repository.LandingConfigRepository;
import com.sanshuiyuan.h5.infra.repository.LandingFeatureRepository;
import com.sanshuiyuan.h5.infra.repository.LandingTrustBadgeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

/**
 * 落地页配置读服务：L1 Caffeine → L2 Redis → DB（plan §6），出口合规校验（Phase C）后才回填缓存。
 * Redis 缺失/异常时优雅降级，绝不阻断公开首页接口。
 */
@Service
public class LandingConfigService {

    private static final Logger log = LoggerFactory.getLogger(LandingConfigService.class);
    private static final String CAFFEINE_KEY = "published";

    private final LandingConfigRepository configRepo;
    private final LandingFeatureRepository featureRepo;
    private final LandingTrustBadgeRepository badgeRepo;
    private final ObjectMapper objectMapper;
    private final CacheManager cacheManager;
    private final StringRedisTemplate redis;
    private final ComplianceTextValidator complianceValidator;

    /** 内存「上一份合规快照」：合规校验失败时回退，避免把违规文案推到 C 端（FR-4.3）。 */
    private volatile LandingConfigResponse lastGoodSnapshot;

    @Value("${landing.cache.redis-key:landing:config:published}")
    private String redisKey;

    @Value("${landing.cache.redis-ttl-seconds:300}")
    private long redisTtlSeconds;

    public LandingConfigService(LandingConfigRepository configRepo,
                                LandingFeatureRepository featureRepo,
                                LandingTrustBadgeRepository badgeRepo,
                                ObjectMapper objectMapper,
                                CacheManager cacheManager,
                                StringRedisTemplate redis,
                                ComplianceTextValidator complianceValidator) {
        this.configRepo = configRepo;
        this.featureRepo = featureRepo;
        this.badgeRepo = badgeRepo;
        this.objectMapper = objectMapper;
        this.cacheManager = cacheManager;
        this.redis = redis;
        this.complianceValidator = complianceValidator;
    }

    /**
     * 读取当前生效（PUBLISHED）落地页配置。读路径：Caffeine → Redis → DB。
     * 无 PUBLISHED 配置抛 {@link ErrorCode#NO_ACTIVE_CONFIG}（前端回退默认文案）。
     */
    @Transactional(readOnly = true)
    public LandingConfigResponse getPublishedConfig() {
        Cache l1 = cacheManager.getCache(CacheConfig.LANDING_CONFIG_CACHE);
        if (l1 != null) {
            LandingConfigResponse cached = l1.get(CAFFEINE_KEY, LandingConfigResponse.class);
            if (cached != null) {
                return cached;
            }
        }

        LandingConfigResponse fromRedis = readRedis();
        if (fromRedis != null) {
            putL1(l1, fromRedis);
            return fromRedis;
        }

        LandingConfigResponse assembled = assembleFromDb();

        // 出口合规校验（FR-4.3）：组装后、回填缓存前递归扫描所有字符串字段。
        // 命中违禁词 → WARN 告警 + 回退上一份合规快照（不抛 500 给 C 端，避免首页挂掉）。
        try {
            complianceValidator.assertCompliant(assembled);
        } catch (ComplianceException ce) {
            log.warn("落地页配置出口合规校验失败 hits={}，回退上一份合规快照（不外泄违禁文案）", ce.hits());
            if (lastGoodSnapshot != null) {
                return lastGoodSnapshot;
            }
            // 无可用快照：拒绝下发违规配置，按「无生效配置」处理（前端回退内置默认）。
            throw new BizException(ErrorCode.NO_ACTIVE_CONFIG, "当前配置未通过合规校验且无可用合规快照");
        }

        lastGoodSnapshot = assembled;
        writeRedis(assembled);
        putL1(l1, assembled);
        return assembled;
    }

    LandingConfigResponse assembleFromDb() {
        LandingConfig cfg = configRepo
                .findFirstByStatusOrderByPublishedAtDesc(ConfigStatus.PUBLISHED)
                .orElseThrow(() -> new BizException(ErrorCode.NO_ACTIVE_CONFIG));

        HeroDto hero = parse(cfg.getHeroJson(), HeroDto.class, "hero");
        SimulatorDto simulator = parse(cfg.getSimulatorJson(), SimulatorDto.class, "simulator");
        FooterDto footer = parse(cfg.getFooterJson(), FooterDto.class, "footer");

        List<FeatureDto> features = featureRepo.findByConfigIdOrderBySortAsc(cfg.getId()).stream()
                .map(f -> new FeatureDto(f.getId(), f.getIconKey(), f.getTitle(), f.getSubtitle(),
                        f.getDescr(), f.getSort()))
                .toList();

        List<TrustBadgeDto> badges = badgeRepo.findByConfigIdOrderBySortAsc(cfg.getId()).stream()
                .map(b -> new TrustBadgeDto(b.getId(), b.getIconKey(), b.getTitle(), b.getSubtitle(), b.getSort()))
                .toList();

        return new LandingConfigResponse(cfg.getVersion(), hero, features, simulator, badges, footer);
    }

    private <T> T parse(String json, Class<T> type, String field) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.error("解析 {}_json 失败", field, e);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "落地页配置 " + field + " 解析失败");
        }
    }

    private void putL1(Cache l1, LandingConfigResponse value) {
        if (l1 != null) {
            l1.put(CAFFEINE_KEY, value);
        }
    }

    private LandingConfigResponse readRedis() {
        if (redis == null) {
            return null;
        }
        try {
            String json = redis.opsForValue().get(redisKey);
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, LandingConfigResponse.class);
        } catch (Exception e) {
            log.debug("Redis L2 读取不可用，降级到 DB：{}", e.getMessage());
            return null;
        }
    }

    private void writeRedis(LandingConfigResponse value) {
        if (redis == null) {
            return;
        }
        try {
            redis.opsForValue().set(redisKey, objectMapper.writeValueAsString(value),
                    Duration.ofSeconds(redisTtlSeconds));
        } catch (Exception e) {
            log.debug("Redis L2 回填不可用，跳过：{}", e.getMessage());
        }
    }
}
