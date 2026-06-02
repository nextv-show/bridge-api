package com.sanshuiyuan.matching.request.application;

import com.sanshuiyuan.matching.identity.MatchingUserResolver;
import com.sanshuiyuan.matching.request.api.ApiException;
import com.sanshuiyuan.matching.request.api.dto.RequestItem;
import com.sanshuiyuan.matching.request.domain.MatchingRequest;
import com.sanshuiyuan.matching.request.domain.PriceTier;
import com.sanshuiyuan.matching.request.infra.MatchingRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * FR-2 nearby：Owner 门控 + bbox + Haversine 精算 + min_price_tier 过滤。
 * 半径查询用 bounding-box（lat/lng 范围扫 idx_status_lat）+ 内存 Haversine ≤radius，
 * 不用 geohash6 3×3（geohash6≈1.2km 覆盖不了 50–200km）。手机号脱敏（未接单 owner）。
 */
@Service
public class NearbyQueryService {

    private final MatchingRequestRepository repo;
    private final MatchingUserResolver userResolver;
    private final MatchingConfigService configService;
    private final RequestItemMapper mapper;

    public NearbyQueryService(MatchingRequestRepository repo,
                              MatchingUserResolver userResolver,
                              MatchingConfigService configService,
                              RequestItemMapper mapper) {
        this.repo = repo;
        this.userResolver = userResolver;
        this.configService = configService;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<RequestItem> nearby(String subject, double lat, double lng,
                                    Double radiusKmParam, String minPriceTierParam) {
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

        PriceTier minTier = parseTier(minPriceTierParam);

        // bbox：Δlat=r/111；Δlng=r/(111*cos(lat))。高纬 cos→0 时 Δlng 放宽（保守扩大候选）。
        double dLat = radiusKm / 111.0;
        double cosLat = Math.cos(Math.toRadians(lat));
        double dLng = (Math.abs(cosLat) < 1e-6) ? 180.0 : radiusKm / (111.0 * Math.abs(cosLat));

        BigDecimal latMin = BigDecimal.valueOf(lat - dLat);
        BigDecimal latMax = BigDecimal.valueOf(lat + dLat);
        BigDecimal lngMin = BigDecimal.valueOf(lng - dLng);
        BigDecimal lngMax = BigDecimal.valueOf(lng + dLng);

        List<MatchingRequest> candidates =
                repo.findOpenInBoundingBox(latMin, latMax, lngMin, lngMax, userId);

        final double radius = radiusKm;
        List<ItemWithDist> result = new ArrayList<>();
        for (MatchingRequest r : candidates) {
            if (minTier != null && !r.getExpectedPriceTier().atLeast(minTier)) {
                continue;   // Q1 min_price_tier 服务端过滤
            }
            double dist = GeoHashIndexer.distanceKm(
                    lat, lng, r.getLat().doubleValue(), r.getLng().doubleValue());
            if (dist > radius) {
                continue;   // bbox 是外接矩形，需 Haversine 精算裁圆
            }
            double rounded = BigDecimal.valueOf(dist).setScale(2, RoundingMode.HALF_UP).doubleValue();
            result.add(new ItemWithDist(r, rounded));
        }
        result.sort(Comparator.comparingDouble(ItemWithDist::dist));

        // 未接单 owner 看脱敏手机号。
        return result.stream().map(iw -> mapper.toItem(iw.req(), false, iw.dist())).toList();
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

    private record ItemWithDist(MatchingRequest req, double dist) {}
}
