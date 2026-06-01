package com.sanshuiyuan.matching.geo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoHashTest {

    @Test
    void encode6_knownValue() {
        // 已知参考点：约 (57.64911, 10.40744) → "u4pruy" 前缀（标准 geohash 测试向量）。
        String h = GeoHash.encode(57.64911, 10.40744, 6);
        assertEquals("u4pruy", h);
    }

    @Test
    void encode6_length() {
        assertEquals(6, GeoHash.encode6(31.2304, 121.4737).length());
    }

    @Test
    void encode6_nearbyPointsSharePrefix() {
        // 相距 ~100m 的两点应共享 geohash6 前缀。
        String a = GeoHash.encode6(31.2304, 121.4737);
        String b = GeoHash.encode6(31.2308, 121.4740);
        assertEquals(a.substring(0, 5), b.substring(0, 5));
    }

    @Test
    void haversine_zeroDistance() {
        assertEquals(0.0, GeoHash.haversineKm(31.23, 121.47, 31.23, 121.47), 1e-9);
    }

    @Test
    void haversine_shanghaiToBeijing() {
        // 上海人民广场(31.2304,121.4737) ↔ 北京天安门(39.9087,116.3975) ≈ 1067km。
        double d = GeoHash.haversineKm(31.2304, 121.4737, 39.9087, 116.3975);
        assertTrue(d > 1050 && d < 1080, "distance was " + d);
    }

    @Test
    void haversine_oneDegreeLatApprox111km() {
        double d = GeoHash.haversineKm(0.0, 0.0, 1.0, 0.0);
        assertTrue(d > 110 && d < 112, "distance was " + d);
    }
}
