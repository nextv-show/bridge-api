package com.sanshuiyuan.matching.request.application;

import com.sanshuiyuan.matching.identity.MatchingUserResolver;
import com.sanshuiyuan.matching.request.api.ApiException;
import com.sanshuiyuan.matching.request.api.dto.RequestItem;
import com.sanshuiyuan.matching.request.domain.MatchingRequest;
import com.sanshuiyuan.matching.request.domain.PriceTier;
import com.sanshuiyuan.matching.request.domain.SceneType;
import com.sanshuiyuan.matching.request.infra.MatchingRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * FR-2 + P1-1 nearby：Owner 门控 → bbox 候选（近端截断 + scene/tier 下推） → Haversine 裁圆
 * → 分桶评分 → 排序（带 tie-breaker） → 分页（design §2 §5）。手机号脱敏（未接单 owner）。
 *
 * <p>两层架构守 NFR P95≤400ms@10万 OPEN：第一层 DB 按平面近似距离只取最近 ≤candidate.limit 条候选
 * （近端优先，不偏向任一排序），第二层应用层算 Haversine/分/排序/分页。撮合分仅用于排序，不进 DTO。
 */
@Service
public class NearbyQueryService {

    private static final Logger log = LoggerFactory.getLogger(NearbyQueryService.class);

    private final MatchingRequestRepository repo;
    private final MatchingUserResolver userResolver;
    private final MatchingConfigService configService;
    private final RequestItemMapper mapper;
    private final RankScorer scorer;

    public NearbyQueryService(MatchingRequestRepository repo,
                              MatchingUserResolver userResolver,
                              MatchingConfigService configService,
                              RequestItemMapper mapper,
                              RankScorer scorer) {
        this.repo = repo;
        this.userResolver = userResolver;
        this.configService = configService;
        this.mapper = mapper;
        this.scorer = scorer;
    }

    /** 排序模式（design §2.3）。 */
    public enum SortMode {
        RECOMMENDED, DISTANCE, REVENUE, TIER, LATEST;

        static SortMode parse(String raw) {
            if (raw == null || raw.isBlank()) return RECOMMENDED;
            try {
                return SortMode.valueOf(raw.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw ApiException.unprocessable("INVALID_SORT",
                        "sort 必须是 recommended/distance/revenue/tier/latest 之一");
            }
        }
    }

    @Transactional(readOnly = true)
    public List<RequestItem> nearby(String subject, double lat, double lng,
                                    Double radiusKmParam, String minPriceTierParam,
                                    String sceneTypeParam, String sortParam,
                                    int page, int size, boolean includeSelf) {
        // 只读解析，不创建 users 行。无 users 行者必然无 device_assets → 非 owner。
        long userId = userResolver.findUserId(subject)
                .orElseThrow(() -> ApiException.forbidden("NOT_OWNER", "仅持机用户可查看附近需求"));
        if (!userResolver.isOwner(userId)) {
            throw ApiException.forbidden("NOT_OWNER", "仅持机用户可查看附近需求");
        }

        // includeSelf=true 时传 null（不排除自己的需求），false 时传实际 userId
        Long excludeUserId = includeSelf ? null : userId;

        Set<PriceTier> tiers = allowedTiers(parseTier(minPriceTierParam));
        SceneType sceneType = parseScene(sceneTypeParam);
        SortMode sort = SortMode.parse(sortParam);
        int candidateLimit = configService.nearbyCandidateLimit();

        // radius_km 缺省 = 「距离不限」模式：跨城/跨省运营场景，返回全部 OPEN（按距离就近排序），不裁圆。
        boolean unlimited = (radiusKmParam == null);

        // unlimited 模式下 radius 取 MAX_VALUE，Haversine 过滤自然恒不裁圆（仍计算距离用于排序/展示）。
        final double radius;
        List<MatchingRequest> candidates;
        if (unlimited) {
            radius = Double.MAX_VALUE;
            candidates = repo.findAllOpenCandidates(
                    excludeUserId, sceneType, tiers,
                    BigDecimal.valueOf(lat), BigDecimal.valueOf(lng),
                    PageRequest.of(0, candidateLimit));
            if (candidates.size() >= candidateLimit) {
                log.warn("nearby(UNLIMITED) candidate truncated at limit={} (lat={}, lng={}); "
                        + "远端/深翻页结果可能不全", candidateLimit, lat, lng);
            }
        } else {
            double defaultRadius = configService.nearbyDefaultRadiusKm();
            double maxRadius = configService.nearbyMaxRadiusKm();
            double radiusKm = radiusKmParam;
            if (radiusKm <= 0) radiusKm = defaultRadius;
            if (radiusKm > maxRadius) radiusKm = maxRadius;   // 越界裁剪到上限
            radius = radiusKm;

            // bbox：Δlat=r/111；Δlng=r/(111*cos(lat))。高纬 cos→0 时 Δlng 放宽（保守扩大候选）。
            double dLat = radiusKm / 111.0;
            double cosLat = Math.cos(Math.toRadians(lat));
            double dLng = (Math.abs(cosLat) < 1e-6) ? 180.0 : radiusKm / (111.0 * Math.abs(cosLat));

            BigDecimal latMin = BigDecimal.valueOf(lat - dLat);
            BigDecimal latMax = BigDecimal.valueOf(lat + dLat);
            BigDecimal lngMin = BigDecimal.valueOf(lng - dLng);
            BigDecimal lngMax = BigDecimal.valueOf(lng + dLng);

            candidates = repo.findOpenCandidates(
                    latMin, latMax, lngMin, lngMax, excludeUserId, sceneType, tiers,
                    BigDecimal.valueOf(lat), BigDecimal.valueOf(lng),
                    PageRequest.of(0, candidateLimit));
            if (candidates.size() >= candidateLimit) {
                // 高密度区截断：候选已被裁到近端 candidateLimit 条（design §7 不静默截断）。
                log.warn("nearby candidate truncated at limit={} (lat={}, lng={}, radiusKm={}); "
                        + "远端/深翻页结果可能不全", candidateLimit, lat, lng, radiusKm);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        double epsilon = configService.jitterEpsilon();

        List<Scored> scored = new ArrayList<>();
        for (MatchingRequest r : candidates) {
            double dist = GeoHashIndexer.distanceKm(
                    lat, lng, r.getLat().doubleValue(), r.getLng().doubleValue());
            if (dist > radius) {
                continue;   // bbox 是外接矩形，需 Haversine 精算裁圆（unlimited 时 radius=MAX 恒不裁）
            }
            double rounded = BigDecimal.valueOf(dist).setScale(2, RoundingMode.HALF_UP).doubleValue();
            // P1 无偏好沉淀（P3 引入），场景偏好传空集 → 场景分恒中性。
            RankScorer.Ranked ranked = scorer.rank(r, rounded, EnumSet.noneOf(SceneType.class), now);
            // 抖动独立保存，仅作 recommended 同分（同桶）tie-breaker，不混入 score（避免错排相差<epsilon 的单）。
            double jitter = RankScorer.jitter(r.getId(), epsilon);
            scored.add(new Scored(r, rounded, ranked.score(), jitter, ranked.monthlyGmv(), ranked.reasons()));
        }

        if (scored.isEmpty()) {
            log.warn("nearby returned 0 results | subject={} lat={} lng={} radiusKm={} mode={} candidateCount={}",
                    subject, lat, lng, radiusKmParam, unlimited ? "UNLIMITED" : "FIXED", candidates.size());
        }

        scored.sort(comparator(sort));

        int from = Math.max(0, page) * Math.max(1, size);
        if (from >= scored.size()) return List.of();
        int to = Math.min(scored.size(), from + Math.max(1, size));

        // 未接单 owner 看脱敏手机号；recommend_reasons 仅 nearby 带值。
        return scored.subList(from, to).stream()
                .map(s -> mapper.toItem(s.req(), false, s.dist(), userId, s.reasons()))
                .toList();
    }

    /** min_price_tier 展开为允许档位集合（atLeast 语义）；无过滤=全 4 档（SQL IN 不接受空集）。 */
    private Set<PriceTier> allowedTiers(PriceTier minTier) {
        if (minTier == null) return EnumSet.allOf(PriceTier.class);
        Set<PriceTier> set = EnumSet.noneOf(PriceTier.class);
        for (PriceTier t : PriceTier.values()) {
            if (t.atLeast(minTier)) set.add(t);
        }
        return set;
    }

    private PriceTier parseTier(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return PriceTier.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            throw ApiException.unprocessable("INVALID_PRICE_TIER",
                    "min_price_tier 必须是 T_040/T_080/T_120/T_150 之一");
        }
    }

    private SceneType parseScene(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return SceneType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.unprocessable("INVALID_SCENE_TYPE",
                    "scene_type 必须是 HOME/OFFICE/SHOP/CAMPUS 之一");
        }
    }

    /**
     * 主排序键 + 统一 tie-breaker：distance asc, created_at desc, id desc（design §2.4）。
     * RECOMMENDED 在主键（score desc）之后、通用 tie-breaker 之前插入 jitter desc：
     * 仅在 score 完全相等（同桶）时由 jitter 打散，绝不改变不同分单的相对次序。
     */
    private static Comparator<Scored> comparator(SortMode sort) {
        Comparator<Scored> tieBreak = Comparator
                .comparingDouble(Scored::dist)
                .thenComparing(Scored::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Comparator.comparingLong(Scored::id).reversed());
        return switch (sort) {
            case RECOMMENDED -> Comparator.comparingDouble(Scored::score).reversed()
                    .thenComparing(Comparator.comparingDouble(Scored::jitter).reversed())
                    .thenComparing(tieBreak);
            case DISTANCE -> Comparator.comparingDouble(Scored::dist).thenComparing(tieBreak);
            case REVENUE -> Comparator.comparing(Scored::gmv).reversed().thenComparing(tieBreak);
            case TIER -> Comparator.comparingInt((Scored s) -> s.req().getExpectedPriceTier().order())
                    .reversed().thenComparing(tieBreak);
            case LATEST -> Comparator.comparing(Scored::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(tieBreak);
        };
    }

    private record Scored(MatchingRequest req, double dist, double score, double jitter,
                          BigDecimal gmv, List<String> reasons) {
        long id() { return req.getId(); }
        LocalDateTime createdAt() { return req.getCreatedAt(); }
    }
}
