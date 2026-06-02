package com.sanshuiyuan.matching.request.application;

import com.sanshuiyuan.matching.geo.GeoHash;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** 落库前生成 geohash6 + 暴露 Haversine 距离静态方法（薄封装，便于注入/测试）。 */
@Component
public class GeoHashIndexer {

    public String geohash6(BigDecimal lat, BigDecimal lng) {
        return GeoHash.encode6(lat.doubleValue(), lng.doubleValue());
    }

    public static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        return GeoHash.haversineKm(lat1, lng1, lat2, lng2);
    }
}
