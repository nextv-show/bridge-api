package com.sanshuiyuan.matching.request.application;

import com.sanshuiyuan.matching.identity.MatchingUserResolver;
import com.sanshuiyuan.matching.request.api.ApiException;
import com.sanshuiyuan.matching.request.api.dto.RequestItem;
import com.sanshuiyuan.matching.request.domain.MatchingRequest;
import com.sanshuiyuan.matching.request.domain.PriceTier;
import com.sanshuiyuan.matching.request.domain.SceneType;
import com.sanshuiyuan.matching.request.infra.MatchingRequestRepository;
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
 * FR-2 + P1-1 nearby：Owner 门控 → bbox 候选（DB 截断 + scene/tier 下推） → Haversine 裁圆
 * → 分桶评分 → 排序（带 tie-breaker） → 分页（design §2 §5）。手机号脱敏（未接单 owner）。
 *
 * <p>两层架构守 NFR P95≤400ms@10万 OPEN：第一层 DB 只取 ≤candidate.limit 条候选，
 * 第二层应用层算距离/分/排序/分页。撮合分仅用于排序，不进 DTO。
 */
@Service
public class NearbyQueryService {

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
                                    int page, int size) {
        // 只读解析，不创建 users 行。无 users 行者必然无 device_assets → 非 owner。
        long userId = userResolver.findUserId(subject)
                .orElseThrow(() -> ApiException.forbidden("NOT_OWNER", "仅持机用户可查看附近需求"));
        if (!userResolver.isOwner(userId)) {
            throw ApiException.forbidden("NOT_OWNER", "仅持机用户可查看附近需求");
        }

        double defaultRadius = configService.nearbyDefaultRadiusKm();
        double maxRadius = configService.nearbyMaxRadiusKm();
        double radiusKm = (radiusKmParam == null) ? defaultRadius : radiusKmParam;
        if (radiusKm <= 0) radiusKm = defaultRadius;
        if (radiusKm > maxRadius) radiusKm = maxRadius;   // 越界裁剪到上限

        Set<PriceTier> tiers = allowedTiers(parseTier(minPriceTierParam));
        SceneType sceneType = parseScene(sceneTypeParam);
        SortMode sort = SortMode.parse(sortParam);

        // bbox：Δlat=r/111；Δlng=r/(111*cos(lat))。高纬 cos→0 时 Δlng 放宽（保守扩大候选）。
        double dLat = radiusKm / 111.0;
        double cosLat = Math.cos(Math.toRadians(lat));
        double dLng = (Math.abs(cosLat) < 1e-6) ? 180.0 : radiusKm / (111.0 * Math.abs(cosLat));

        BigDecimal latMin = BigDecimal.valueOf(lat - dLat);
        BigDecimal latMax = BigDecimal.valueOf(lat + dLat);
        BigDecimal lngMin = BigDecimal.valueOf(lng - dLng);
        BigDecimal lngMax = BigDecimal.valueOf(lng + dLng);

        int candidateLimit = configService.nearbyCandidateLimit();
        List<MatchingRequest> candidates = repo.findOpenCandidates(
                latMin, latMax, lngMin, lngMax, userId, sceneType, tiers,
                PageRequest.of(0, candidateLimit));

        final double radius = radiusKm;
        LocalDateTime now = LocalDateTime.now();
        double epsilon = configService.jitterEpsilon();

        List<Scored> scored = new ArrayList<>();
        for (MatchingRequest r : candidates) {
            double dist = GeoHashIndexer.distanceKm(
                    lat, lng, r.getLat().doubleValue(), r.getLng().doubleValue());
            if (dist > radius) {
                continue;   // bbox 是外接矩形，需 Haversine 精算裁圆
            }
            double rounded = BigDecimal.valueOf(dist).setScale(2, RoundingMode.HALF_UP).doubleValue();
            // P1 无偏好沉淀（P3 引入），场景偏好传空集 → 场景分恒中性。
            RankScorer.Ranked ranked = scorer.rank(r, rounded, EnumSet.noneOf(SceneType.class), now);
            // recommended 加同桶稳定抖动（基于 id，翻页一致）。
            double effScore = ranked.score() + RankScorer.jitter(r.getId(), epsilon);
            scored.add(new Scored(r, rounded, effScore, ranked.monthlyGmv(), ranked.reasons()));
        }

        scored.sort(comparator(sort));

        int from = Math.max(0, page) * Math.max(1, size);
        if (from >= scored.size()) return List.of();
        int to = Math.min(scored.size(), from + Math.max(1, size));

        // 未接单 owner 看脱敏手机号；recommend_reasons 仅 nearby 带值。
        return scored.subList(from, to).stream()
                .map(s -> mapper.toItem(s.req(), false, s.dist(), null, s.reasons()))
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

    /** 主排序键 + 统一 tie-breaker：distance asc, created_at desc, id desc（design §2.4）。 */
    private static Comparator<Scored> comparator(SortMode sort) {
        Comparator<Scored> tieBreak = Comparator
                .comparingDouble(Scored::dist)
                .thenComparing(Scored::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Comparator.comparingLong(Scored::id).reversed());
        Comparator<Scored> primary = switch (sort) {
            case RECOMMENDED -> Comparator.comparingDouble(Scored::effScore).reversed();
            case DISTANCE -> Comparator.comparingDouble(Scored::dist);
            case REVENUE -> Comparator.comparing(Scored::gmv).reversed();
            case TIER -> Comparator.comparingInt((Scored s) -> s.req().getExpectedPriceTier().order()).reversed();
            case LATEST -> Comparator.comparing(Scored::createdAt, Comparator.nullsLast(Comparator.reverseOrder()));
        };
        return primary.thenComparing(tieBreak);
    }

    private record Scored(MatchingRequest req, double dist, double effScore,
                          BigDecimal gmv, List<String> reasons) {
        long id() { return req.getId(); }
        LocalDateTime createdAt() { return req.getCreatedAt(); }
    }
}
