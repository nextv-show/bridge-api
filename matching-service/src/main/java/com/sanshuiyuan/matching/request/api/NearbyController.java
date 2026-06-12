package com.sanshuiyuan.matching.request.api;

import com.sanshuiyuan.matching.auth.CurrentUser;
import com.sanshuiyuan.matching.request.api.dto.RequestItem;
import com.sanshuiyuan.matching.request.application.NearbyQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** FR-2 nearby：Owner 门控的附近需求列表。 */
@RestController
@RequestMapping("/api/matching/requests")
public class NearbyController {

    private final NearbyQueryService nearbyQueryService;

    public NearbyController(NearbyQueryService nearbyQueryService) {
        this.nearbyQueryService = nearbyQueryService;
    }

    @GetMapping("/nearby")
    public List<RequestItem> nearby(
            @RequestParam("lat") double lat,
            @RequestParam("lng") double lng,
            @RequestParam(value = "radius_km", required = false) Double radiusKm,
            @RequestParam(value = "min_price_tier", required = false) String minPriceTier,
            @RequestParam(value = "scene_type", required = false) String sceneType,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "20") int size) {
        return nearbyQueryService.nearby(CurrentUser.subject(), lat, lng, radiusKm,
                minPriceTier, sceneType, sort, page, size);
    }
}
